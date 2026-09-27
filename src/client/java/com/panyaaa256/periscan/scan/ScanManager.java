package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.PeriDimension;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.zone.Zone;
import com.panyaaa256.periscan.zone.ZoneLayout;
import com.panyaaa256.periscan.zone.ZoneMatcher;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class ScanManager {
	public static final ScanManager INSTANCE = new ScanManager();

	// Height of the bottom trench zone: the lowest scanned layers of the trench body.
	private static final int BOTTOM_TRENCH_LAYERS = 2;

	private ZoneLayout layout;
	private ResourceKey<Level> dimension;
	private ChunkPos cornerA;
	private ChunkPos cornerB;
	private final EnumMap<Zone, ZoneMatcher> matchers = new EnumMap<>(Zone.class);
	private final EnumMap<Zone, LongOpenHashSet> highlights = new EnumMap<>(Zone.class);
	// Falling-block runs in the trench body, split by run direction so that a corner
	// position qualifying in one direction is not clobbered by a recompute of the other.
	private final LongOpenHashSet fallingAlongX = new LongOpenHashSet();
	private final LongOpenHashSet fallingAlongZ = new LongOpenHashSet();
	// Lines (direction + cross coordinate + y) whose falling runs need recomputing.
	private final LongOpenHashSet dirtyFallingLines = new LongOpenHashSet();
	private final LongOpenHashSet pendingChunks = new LongOpenHashSet();
	private int tickCounter = 0;
	private boolean dormantNoticePending = false;

	private ScanManager() {
		for (Zone zone : Zone.VALUES) {
			highlights.put(zone, new LongOpenHashSet());
		}
	}

	public void init() {
		ClientChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> deactivate());
	}

	public boolean isActive() {
		return layout != null;
	}

	/** Dimension the region belongs to; scanning, validation and rendering only happen there. */
	public ResourceKey<Level> dimension() {
		return dimension;
	}

	/**
	 * Shows the "saved profile available, run /peri scan reload" chat notice on the
	 * next tick. Used when a saved region is found on login; nothing is scanned
	 * until the user actually reloads.
	 */
	public void showDormantNotice() {
		this.dormantNoticePending = true;
	}

	public LongOpenHashSet highlights(Zone zone) {
		return highlights.get(zone);
	}

	/** Falling-block run highlights with runs along the X axis. Do not modify. */
	public LongOpenHashSet fallingAlongX() {
		return fallingAlongX;
	}

	/** Falling-block run highlights with runs along the Z axis. Do not modify. */
	public LongOpenHashSet fallingAlongZ() {
		return fallingAlongZ;
	}

	public int pendingChunkCount() {
		return pendingChunks.size();
	}

	/** Chunks in the region not scanned yet (as ChunkPos longs). Do not modify. */
	public LongOpenHashSet pendingChunks() {
		return pendingChunks;
	}

	/**
	 * Activates highlighting for the given perimeter (chunk coordinates, outermost
	 * rectangle) in the given dimension. Returns config entries that could not be
	 * parsed, for feedback.
	 */
	public List<String> activate(ResourceKey<Level> dim, ChunkPos a, ChunkPos b) {
		PeriScanConfig config = PeriScanConfig.get();
		if (!PeriScanConfig.anyZoneEnabled()) {
			// Nothing to scan; keep the region so a later reload can start it.
			deactivateKeepingRegion(dim, a, b);
			return List.of();
		}
		this.dimension = dim;
		this.cornerA = a;
		this.cornerB = b;
		this.layout = ZoneLayout.of(a, b, config);

		List<String> invalidEntries = new ArrayList<>();
		ZoneMatcher.WaterloggedExclusions exclusions = ZoneMatcher.WaterloggedExclusions.compile(
				config.waterloggedBlacklist, config.waterloggedExcludePushDestroy, invalidEntries);
		matchers.clear();
		for (Zone zone : Zone.VALUES) {
			matchers.put(zone, zone.compileMatcher(config, layout, exclusions, invalidEntries));
		}

		clearScanResults();
		for (ChunkPos chunk : layout.chunks()) {
			pendingChunks.add(chunk.toLong());
		}

		// Scan whatever is already loaded; the rest is picked up by CHUNK_LOAD.
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.dimension() == dim) {
			LongIterator it = pendingChunks.iterator();
			List<LevelChunk> loaded = new ArrayList<>();
			while (it.hasNext()) {
				long key = it.nextLong();
				LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
				if (chunk != null) {
					loaded.add(chunk);
					it.remove();
				}
			}
			for (LevelChunk chunk : loaded) {
				scanChunk(level, chunk);
			}
			flushFallingLines(level);
		}
		return invalidEntries;
	}

	/** Re-derives zones/matchers from current config and rescans, keeping the region. */
	public List<String> rescan() {
		if (!isActive()) {
			return List.of();
		}
		if (!PeriScanConfig.anyZoneEnabled()) {
			// All zones were just disabled: stop scanning but keep the region dormant.
			deactivateKeepingRegion(dimension, cornerA, cornerB);
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.displayClientMessage(Component.translatable("periscan.msg.all_disabled"), false);
			}
			return List.of();
		}
		return activate(dimension, cornerA, cornerB);
	}

	public void deactivate() {
		layout = null;
		dimension = null;
		cornerA = null;
		cornerB = null;
		dormantNoticePending = false;
		matchers.clear();
		clearScanResults();
	}

	/** Stops scanning but remembers the region, so rescan/reload can restart it. */
	private void deactivateKeepingRegion(ResourceKey<Level> dim, ChunkPos a, ChunkPos b) {
		deactivate();
		this.dimension = dim;
		this.cornerA = a;
		this.cornerB = b;
	}

	private void clearScanResults() {
		for (LongOpenHashSet set : highlights.values()) {
			set.clear();
		}
		fallingAlongX.clear();
		fallingAlongZ.clear();
		dirtyFallingLines.clear();
		pendingChunks.clear();
	}

	private void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (layout == null || level.dimension() != dimension || !layout.intersectsChunk(chunk.getPos())) {
			return;
		}
		pendingChunks.remove(chunk.getPos().toLong());
		// Remove stale highlights from this chunk before rescanning it (covers
		// changes that happened while the chunk was unloaded).
		clearChunkHighlights(chunk.getPos());
		scanChunk(level, chunk);
		flushFallingLines(level);
	}

	private void clearChunkHighlights(ChunkPos pos) {
		for (LongOpenHashSet set : highlights.values()) {
			set.removeIf(key -> (BlockPos.getX(key) >> 4) == pos.x && (BlockPos.getZ(key) >> 4) == pos.z);
		}
		clearChunkFalling(fallingAlongX, true, pos);
		clearChunkFalling(fallingAlongZ, false, pos);
	}

	/**
	 * Runs span chunks, so a run may have been cut by changes made while this chunk
	 * was unloaded: mark the lines of its cleared highlights dirty so the parts in
	 * neighboring chunks are re-evaluated after the rescan.
	 */
	private void clearChunkFalling(LongOpenHashSet set, boolean alongX, ChunkPos pos) {
		set.removeIf(key -> {
			if ((BlockPos.getX(key) >> 4) != pos.x || (BlockPos.getZ(key) >> 4) != pos.z) {
				return false;
			}
			dirtyFallingLines.add(fallingLineKey(alongX,
					alongX ? BlockPos.getZ(key) : BlockPos.getX(key), BlockPos.getY(key)));
			return true;
		});
	}

	private void scanChunk(ClientLevel level, LevelChunk chunk) {
		ChunkPos cp = chunk.getPos();
		int baseX = cp.getMinBlockX();
		int baseZ = cp.getMinBlockZ();

		byte[] columnMasks = new byte[256];
		boolean anyColumn = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int mask = layout.zoneMask(baseX + dx, baseZ + dz);
				columnMasks[(dx << 4) | dz] = (byte) mask;
				anyColumn |= mask != 0;
			}
		}
		if (!anyColumn) {
			return;
		}

		int minY = scanMinY(level);
		int maxY = scanMaxY(level);
		// The bottom trench zone is the lowest scanned layers; the trench inner
		// zone starts above them.
		int bottomTopY = minY + BOTTOM_TRENCH_LAYERS - 1;
		for (int y = minY; y <= maxY; y++) {
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
			if (section.hasOnlyAir()) {
				// Jump to the end of this section.
				y = (((y >> 4) + 1) << 4) - 1;
				continue;
			}
			int localY = y & 15;
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					int mask = columnMasks[(dx << 4) | dz];
					if (mask == 0) {
						continue;
					}
					BlockState state = section.getBlockState(dx, localY, dz);
					if (state.isAir()) {
						continue;
					}
					for (Zone zone : Zone.VALUES) {
						if ((mask & zone.mask()) == 0) {
							continue;
						}
						if (zone == Zone.BOTTOM_TRENCH ? y > bottomTopY : zone == Zone.TRENCH_INNER && y <= bottomTopY) {
							continue;
						}
						if (matchers.get(zone).matches(state, baseX + dx, baseZ + dz)) {
							highlights.get(zone).add(BlockPos.asLong(baseX + dx, y, baseZ + dz));
						}
					}
				}
			}
		}

		collectFallingLines(level, chunk);
	}

	// ---- Falling-block runs in the trench body ----
	//
	// A run is a straight horizontal line of blocks along the trencher's direction
	// of travel (the strip's long axis). Falling blocks count toward the run; air,
	// liquids and blocks destroyed by a piston push are skipped (they neither count
	// nor break the run); only other blocks end it. Runs with at least
	// config.trenchInner.fallingRunLength falling blocks get all their falling blocks
	// highlighted. Runs can span chunks, so chunk scans only mark affected lines
	// dirty and whole lines are recomputed afterwards.

	private static boolean countsInFallingRun(BlockState state) {
		return state.getBlock() instanceof Fallable && state.getPistonPushReaction() != PushReaction.DESTROY;
	}

	private static boolean skipsFallingRun(BlockState state) {
		// Liquids have PushReaction.DESTROY, so they are covered here too.
		return state.isAir() || state.getPistonPushReaction() == PushReaction.DESTROY;
	}

	private static boolean breaksFallingRun(BlockState state) {
		return !countsInFallingRun(state) && !skipsFallingRun(state);
	}

	// direction flag (1 bit) | y (12 bits, offset) | cross coordinate (high 32 bits)
	private static long fallingLineKey(boolean alongX, int cross, int y) {
		return ((long) cross << 32) | ((long) (y + 2048) << 1) | (alongX ? 1L : 0L);
	}

	/** Marks every falling-run line that this chunk might affect as dirty. */
	private void collectFallingLines(ClientLevel level, LevelChunk chunk) {
		List<ZoneLayout.TrenchStrip> strips = layout.trenchStrips();
		if (strips.isEmpty()) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		int chunkMinX = cp.getMinBlockX();
		int chunkMinZ = cp.getMinBlockZ();
		// The lowest layers belong to the bottom trench zone, not the trench inner.
		int minY = scanMinY(level) + BOTTOM_TRENCH_LAYERS;
		int maxY = scanMaxY(level);
		for (ZoneLayout.TrenchStrip strip : strips) {
			ZoneLayout.Rect rect = strip.rect();
			int x0 = Math.max(rect.minX(), chunkMinX);
			int x1 = Math.min(rect.maxX(), chunkMinX + 15);
			int z0 = Math.max(rect.minZ(), chunkMinZ);
			int z1 = Math.min(rect.maxZ(), chunkMinZ + 15);
			if (x0 > x1 || z0 > z1) {
				continue;
			}
			for (int y = minY; y <= maxY; y++) {
				LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
				if (section.hasOnlyAir()) {
					// Air is skipped by runs, so it can bridge runs of neighboring
					// chunks across this one: every line here needs a recompute.
					int sectionEnd = Math.min(maxY, (((y >> 4) + 1) << 4) - 1);
					for (; y <= sectionEnd; y++) {
						for (int cross = strip.alongX() ? z0 : x0, max = strip.alongX() ? z1 : x1; cross <= max; cross++) {
							dirtyFallingLines.add(fallingLineKey(strip.alongX(), cross, y));
						}
					}
					y--; // the outer loop increments again
					continue;
				}
				int localY = y & 15;
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						BlockState state = section.getBlockState(x & 15, localY, z & 15);
						// Skipped blocks matter too: they can bridge runs across this chunk.
						if (!breaksFallingRun(state)) {
							dirtyFallingLines.add(fallingLineKey(strip.alongX(), strip.alongX() ? z : x, y));
						}
					}
				}
			}
		}
	}

	private void flushFallingLines(ClientLevel level) {
		if (dirtyFallingLines.isEmpty()) {
			return;
		}
		if (layout != null) {
			LongIterator it = dirtyFallingLines.iterator();
			while (it.hasNext()) {
				long key = it.nextLong();
				recomputeFallingLine(level, (key & 1) != 0, (int) (key >> 32), (int) ((key >>> 1) & 0xFFF) - 2048);
			}
		}
		dirtyFallingLines.clear();
	}

	/**
	 * Recomputes the falling runs of one whole line (trench strips span the full
	 * region length on their axis). Loaded parts of the line are brought up to
	 * date; highlights in unloaded chunks are left as cached.
	 */
	private void recomputeFallingLine(ClientLevel level, boolean alongX, int cross, int y) {
		LongOpenHashSet target = alongX ? fallingAlongX : fallingAlongZ;
		int threshold = Math.max(1, PeriScanConfig.get().trenchInner.fallingRunLength);
		int from = alongX ? layout.regionMinX() : layout.regionMinZ();
		int to = alongX ? layout.regionMaxX() : layout.regionMaxZ();
		int fixedChunk = cross >> 4;
		LongArrayList run = new LongArrayList();
		int pos = from;
		while (pos <= to) {
			int stretchEnd = Math.min(to, pos | 15);
			LevelChunk chunk = level.getChunkSource().getChunkNow(
					alongX ? pos >> 4 : fixedChunk, alongX ? fixedChunk : pos >> 4);
			if (chunk == null) {
				// Unknown territory: end the run and keep whatever is cached there.
				endFallingRun(target, run, threshold);
				pos = stretchEnd + 1;
				continue;
			}
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
			boolean airOnly = section.hasOnlyAir();
			for (; pos <= stretchEnd; pos++) {
				int x = alongX ? pos : cross;
				int z = alongX ? cross : pos;
				long key = BlockPos.asLong(x, y, z);
				BlockState state = airOnly ? null : section.getBlockState(x & 15, y & 15, z & 15);
				if (state != null && countsInFallingRun(state)) {
					run.add(key);
				} else {
					target.remove(key);
					// A null state means an all-air section: skipped like any air.
					if (state != null && breaksFallingRun(state)) {
						endFallingRun(target, run, threshold);
					}
				}
			}
		}
		endFallingRun(target, run, threshold);
	}

	private static void endFallingRun(LongOpenHashSet target, LongArrayList run, int threshold) {
		boolean qualifies = run.size() >= threshold;
		for (int i = 0; i < run.size(); i++) {
			if (qualifies) {
				target.add(run.getLong(i));
			} else {
				target.remove(run.getLong(i));
			}
		}
		run.clear();
	}

	/** Lowest scanned Y (inclusive): the bedrock floor is skipped where it is known. */
	public static int scanMinY(ClientLevel level) {
		PeriDimension dimension = PeriDimension.of(level.dimension());
		return dimension == null ? level.getMinY() : Math.max(level.getMinY(), dimension.scanFloorY());
	}

	/** Highest scanned Y (inclusive), from the config. */
	public static int scanMaxY(ClientLevel level) {
		return Math.min(level.getMaxY(), PeriScanConfig.get().scanMaxY);
	}

	private void onTick(Minecraft client) {
		if (client.level == null) {
			return;
		}
		if (dormantNoticePending && client.player != null) {
			dormantNoticePending = false;
			client.player.displayClientMessage(Component.translatable("periscan.msg.region_available"), false);
		}
		if (layout == null || client.level.dimension() != dimension) {
			return;
		}
		tickCounter++;

		// Drop highlights whose block has been replaced (by anyone) with something
		// that no longer matches. Block updates reach the client, so re-checking the
		// current state covers other players' changes too.
		if (tickCounter % 10 == 0) {
			validateHighlights(client.level);
		}

		if (!pendingChunks.isEmpty() && tickCounter % 20 == 0 && client.player != null) {
			client.player.displayClientMessage(
					Component.translatable("periscan.msg.pending_chunks", pendingChunks.size()), true);
		}
	}

	private void validateHighlights(ClientLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Zone zone : Zone.VALUES) {
			ZoneMatcher matcher = matchers.get(zone);
			if (matcher == null) {
				continue;
			}
			highlights.get(zone).removeIf(key -> {
				pos.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
				if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					return false; // keep cached highlights for unloaded chunks
				}
				return !matcher.matches(level.getBlockState(pos), pos.getX(), pos.getZ());
			});
		}
		validateFallingHighlights(level);
	}

	/**
	 * Re-checks highlighted falling blocks; a replaced one may split its run, so
	 * the whole line is recomputed rather than just the block removed.
	 */
	private void validateFallingHighlights(ClientLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (boolean alongX : new boolean[] { true, false }) {
			LongOpenHashSet set = alongX ? fallingAlongX : fallingAlongZ;
			LongIterator it = set.iterator();
			while (it.hasNext()) {
				long key = it.nextLong();
				pos.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
				if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					continue; // keep cached highlights for unloaded chunks
				}
				if (!countsInFallingRun(level.getBlockState(pos))) {
					dirtyFallingLines.add(fallingLineKey(alongX, alongX ? pos.getZ() : pos.getX(), pos.getY()));
				}
			}
		}
		flushFallingLines(level);
	}
}
