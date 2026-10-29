package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.compat.ChunkPosCompat;
import com.panyaaa256.periscan.network.BlockUpdatesPayload;
import com.panyaaa256.periscan.network.ChunkResultPayload;
import com.panyaaa256.periscan.network.FallingRunsPayload;
import com.panyaaa256.periscan.network.ScanFinishedPayload;
import com.panyaaa256.periscan.network.ScanStartPayload;
import com.panyaaa256.periscan.scan.ChunkIndexedPositions;
import com.panyaaa256.periscan.scan.FallingRunTracker;
import com.panyaaa256.periscan.scan.ZoneScanner;
import com.panyaaa256.periscan.zone.Zone;
import com.panyaaa256.periscan.zone.ZoneLayout;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One player's scan on the server. The chunks of the region are scanned a few
 * at a time (see scanSome): loaded chunks as they are in memory, the others
 * from the save, read in the background. Each chunk's highlights are sent to
 * the player as soon as it is scanned, and ScanFinishedPayload follows the last.
 * Blocks that change in scanned chunks afterwards are sent each tick (see
 * sendBlockUpdates), until the session ends. Falling-block runs are worked out
 * once the scan is done, and kept up to date from then on.
 */
final class ServerScanSession {
	// Chunks being read from the save at once, so a large region does not flood
	// the chunk IO the world itself needs.
	private static final int MAX_READS_IN_FLIGHT = 32;
	// Chunks per FallingRunsPayload, so the first flush of a large region is split.
	private static final int RUN_CHUNKS_PER_PAYLOAD = 256;

	private final UUID player;
	private final int scanId;
	private final ResourceKey<Level> dimension;
	private final ZoneScanner scanner;
	// Chunks (ChunkPos longs) of the region that have not had their turn yet.
	private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
	// Reads finished in the background, waiting for the server thread to scan them.
	private final Queue<SavedChunk> readChunks = new ConcurrentLinkedQueue<>();
	private int readsInFlight;
	private final LongArrayList ungenerated = new LongArrayList();
	private final LongArrayList unreadable = new LongArrayList();
	private boolean finished;
	// Chunks whose highlights the player has (ChunkResultPayload sent).
	private final LongOpenHashSet scanned = new LongOpenHashSet();
	// Positions (BlockPos longs) in the region's zone columns whose block changed this tick.
	private final LongOpenHashSet changedBlocks = new LongOpenHashSet();
	private final int fallingRunLength;
	private final FallingRunTracker fallingRuns = new FallingRunTracker();
	// Made once the level, and so the scanned height, is known (see runBlocks()).
	private RunBlockCache runBlocks;

	ServerScanSession(UUID player, ScanStartPayload request) {
		this.player = player;
		this.scanId = request.scanId();
		this.dimension = request.dimension();
		// Problems with the entries are not reported from here: the client compiles
		// the same settings itself and shows them.
		this.scanner = new ZoneScanner(request.cornerA(), request.cornerB(), request.settings(), new ArrayList<>());
		this.fallingRunLength = request.settings().fallingRunLength();
		for (ChunkPos chunk : scanner.layout().chunks()) {
			queue.enqueue(ChunkPosCompat.chunkKey(chunk));
		}
	}

	/** Whether every chunk has had its turn, the player has been told, and the runs are up to date. */
	boolean done() {
		return finished && !fallingRuns.hasDirtyLines();
	}

	/** Remembers a block change in this session's dimension and zone columns (see LevelChunkMixin). */
	void onBlockChanged(ResourceKey<Level> level, BlockPos pos) {
		if (level == dimension && scanner.layout().containsColumn(pos.getX(), pos.getZ())) {
			changedBlocks.add(pos.asLong());
		}
	}

	/**
	 * Sends the zones of the blocks that changed this tick in scanned chunks.
	 * Chunks not scanned yet show their blocks as they are when they are.
	 */
	void sendBlockUpdates(MinecraftServer server) {
		if (changedBlocks.isEmpty()) {
			return;
		}
		ServerPlayer target = server.getPlayerList().getPlayer(player);
		ServerLevel level = server.getLevel(dimension);
		if (target == null || level == null) {
			changedBlocks.clear();
			return;
		}
		int minY = ZoneScanner.minY(level);
		int maxY = scanner.maxY(level);
		LongArrayList positions = new LongArrayList();
		ByteArrayList zoneMasks = new ByteArrayList();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LongIterator it = changedBlocks.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			pos.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
			int chunkX = pos.getX() >> 4;
			int chunkZ = pos.getZ() >> 4;
			if (pos.getY() < minY || pos.getY() > maxY
					|| !scanned.contains(ChunkPosCompat.chunkKey(new ChunkPos(chunkX, chunkZ)))) {
				continue;
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk == null) {
				continue;
			}
			BlockState state = chunk.getBlockState(pos);
			int[] mask = {0};
			scanner.scanBlock(pos.getX(), pos.getY(), pos.getZ(), state, minY,
					(zone, ignored) -> mask[0] |= zone.mask());
			positions.add(key);
			zoneMasks.add((byte) mask[0]);
			runBlocks(level).update(pos.getX(), pos.getY(), pos.getZ(), state);
			fallingRuns.markPosition(scanner.layout().trenchStrips(), pos.getX(), pos.getY(), pos.getZ(),
					minY + scanner.bottomTrenchLayers(), maxY);
		}
		changedBlocks.clear();
		if (!positions.isEmpty()) {
			ServerPlayNetworking.send(target, new BlockUpdatesPayload(scanId, positions.toLongArray(), zoneMasks.toByteArray()));
		}
	}

	/**
	 * Scans chunks until there are none left to scan right now or
	 * System.nanoTime() reaches the deadline. Does nothing while the player or
	 * the dimension is missing (the session ends when the player leaves).
	 */
	void scanSome(MinecraftServer server, long deadline) {
		ServerPlayer target = server.getPlayerList().getPlayer(player);
		ServerLevel level = server.getLevel(dimension);
		if (target == null || level == null) {
			return;
		}
		int minY = ZoneScanner.minY(level);
		int maxY = scanner.maxY(level);
		runBlocks(level);

		SavedChunk saved;
		while (System.nanoTime() < deadline && (saved = readChunks.poll()) != null) {
			readsInFlight--;
			int chunkX = ChunkPos.getX(saved.key());
			int chunkZ = ChunkPos.getZ(saved.key());
			// Loaded since it was read: the chunk in memory may have changed already.
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk != null) {
				scan(target, chunkX, chunkZ, chunk.getSections(), chunk.getMinSectionY(), minY, maxY);
				continue;
			}
			switch (saved.state()) {
				case GENERATED -> scan(target, chunkX, chunkZ, saved.sections(), level.getMinSectionY(), minY, maxY);
				case UNGENERATED -> ungenerated.add(saved.key());
				case UNREADABLE -> unreadable.add(saved.key());
			}
		}

		while (!queue.isEmpty() && System.nanoTime() < deadline) {
			long key = queue.firstLong();
			int chunkX = ChunkPos.getX(key);
			int chunkZ = ChunkPos.getZ(key);
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk != null) {
				queue.dequeueLong();
				scan(target, chunkX, chunkZ, chunk.getSections(), chunk.getMinSectionY(), minY, maxY);
			} else if (readsInFlight < MAX_READS_IN_FLIGHT) {
				queue.dequeueLong();
				read(level, key);
			} else {
				break;
			}
		}

		if (!finished && queue.isEmpty() && readsInFlight == 0) {
			finished = true;
			ServerPlayNetworking.send(target, new ScanFinishedPayload(scanId,
					ungenerated.toLongArray(), unreadable.toLongArray()));
		}

		// A run spans the whole region, so its line is only worked out once the
		// scan is done: recomputing it each time a chunk along it was scanned
		// would redo the same lines over and over.
		if (finished && fallingRuns.hasDirtyLines()) {
			fallingRuns.flush(runBlocks, scanner.layout().region(), fallingRunLength, deadline);
			sendFallingRuns(target);
		}
	}

	private RunBlockCache runBlocks(ServerLevel level) {
		if (runBlocks == null) {
			runBlocks = new RunBlockCache(ZoneScanner.minY(level), scanner.maxY(level));
		}
		return runBlocks;
	}

	/** Sends the run highlights of every chunk whose runs changed since the last call. */
	private void sendFallingRuns(ServerPlayer target) {
		LongOpenHashSet changed = new LongOpenHashSet();
		fallingRuns.alongXIndex().drainDirty(changed::add);
		fallingRuns.alongZIndex().drainDirty(changed::add);
		List<FallingRunsPayload.ChunkRuns> chunks = new ArrayList<>();
		LongIterator it = changed.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			chunks.add(new FallingRunsPayload.ChunkRuns(ChunkIndexedPositions.chunkX(key), ChunkIndexedPositions.chunkZ(key),
					positionsIn(fallingRuns.alongXIndex(), key), positionsIn(fallingRuns.alongZIndex(), key)));
			if (chunks.size() == RUN_CHUNKS_PER_PAYLOAD) {
				ServerPlayNetworking.send(target, new FallingRunsPayload(scanId, chunks));
				chunks = new ArrayList<>();
			}
		}
		if (!chunks.isEmpty()) {
			ServerPlayNetworking.send(target, new FallingRunsPayload(scanId, chunks));
		}
	}

	private static long[] positionsIn(ChunkIndexedPositions set, long chunkKey) {
		LongOpenHashSet positions = set.chunkPositions(chunkKey);
		return positions == null ? new long[0] : positions.toLongArray();
	}

	/** Reads a chunk the server has not loaded from the save, in the background. */
	private void read(ServerLevel level, long key) {
		readsInFlight++;
		level.getChunkSource().chunkMap.readChunk(new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)))
				.thenApply(tag -> SavedChunk.parse(level, key, tag))
				.exceptionally(e -> {
					PeriScan.LOGGER.warn("PeriScan: could not read chunk {} for a scan",
							new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)), e);
					return SavedChunk.failed(key);
				})
				.thenAccept(readChunks::add);
	}

	private void scan(ServerPlayer target, int chunkX, int chunkZ, LevelChunkSection[] sections, int minSectionY,
			int minY, int maxY) {
		LongArrayList[] found = new LongArrayList[Zone.VALUES.length];
		for (int i = 0; i < found.length; i++) {
			found[i] = new LongArrayList();
		}
		scanner.scanChunk(chunkX, chunkZ, sections, minSectionY, minY, maxY,
				(zone, pos) -> found[zone.ordinal()].add(pos));
		long[][] positions = new long[found.length][];
		for (int i = 0; i < found.length; i++) {
			positions[i] = found[i].toLongArray();
		}
		ServerPlayNetworking.send(target, new ChunkResultPayload(scanId, chunkX, chunkZ, positions));
		scanned.add(ChunkPosCompat.chunkKey(new ChunkPos(chunkX, chunkZ)));

		List<ZoneLayout.TrenchStrip> strips = scanner.layout().trenchStrips();
		if (strips.stream().anyMatch(strip -> strip.rect().intersectsChunk(chunkX << 4, chunkZ << 4))) {
			runBlocks.storeChunk(chunkX, chunkZ, sections, minSectionY);
			// The lowest layers belong to the bottom trench zone (when enabled), not the trench inner.
			fallingRuns.markChunk(strips, chunkX, chunkZ, minY + scanner.bottomTrenchLayers(), maxY, runBlocks);
		}
	}
}
