package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.compat.ChunkPosCompat;
import com.panyaaa256.periscan.network.ChunkResultPayload;
import com.panyaaa256.periscan.network.ScanFinishedPayload;
import com.panyaaa256.periscan.network.ScanStartPayload;
import com.panyaaa256.periscan.scan.ZoneScanner;
import com.panyaaa256.periscan.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One player's scan on the server. The chunks of the region are scanned a few
 * at a time (see scanSome): loaded chunks as they are in memory, the others
 * from the save, read in the background. Each chunk's highlights are sent to
 * the player as soon as it is scanned, and ScanFinishedPayload follows the last.
 */
final class ServerScanSession {
	// Chunks being read from the save at once, so a large region does not flood
	// the chunk IO the world itself needs.
	private static final int MAX_READS_IN_FLIGHT = 32;

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

	ServerScanSession(UUID player, ScanStartPayload request) {
		this.player = player;
		this.scanId = request.scanId();
		this.dimension = request.dimension();
		// Problems with the entries are not reported from here: the client compiles
		// the same settings itself and shows them.
		this.scanner = new ZoneScanner(request.cornerA(), request.cornerB(), request.settings(), new ArrayList<>());
		for (ChunkPos chunk : scanner.layout().chunks()) {
			queue.enqueue(ChunkPosCompat.chunkKey(chunk));
		}
	}

	/** Whether every chunk has had its turn and the player has been told. */
	boolean done() {
		return finished;
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

		if (queue.isEmpty() && readsInFlight == 0) {
			finished = true;
			ServerPlayNetworking.send(target, new ScanFinishedPayload(scanId,
					ungenerated.toLongArray(), unreadable.toLongArray()));
		}
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
	}
}
