package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.PeriDimension;
import com.panyaaa256.periscan.compat.VersionCompat;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.zone.Zone;
import com.panyaaa256.periscan.zone.ZoneLayout;
import com.panyaaa256.periscan.zone.ZoneMatcher;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScanManager {
	public static final ScanManager INSTANCE = new ScanManager();

	// Height of the bottom trench zone: the lowest scanned layers of the trench body.
	private static final int BOTTOM_TRENCH_LAYERS = 2;

	// Ticks between the safety-net revalidations of the highlights (see onTick).
	private static final int VALIDATE_INTERVAL_TICKS = 20;

	private ZoneLayout layout;
	private ResourceKey<Level> dimension;
	private ChunkPos cornerA;
	private ChunkPos cornerB;
	private final EnumMap<Zone, ZoneMatcher> matchers = new EnumMap<>(Zone.class);
	private final EnumMap<Zone, ChunkIndexedPositions> highlights = new EnumMap<>(Zone.class);
	private final FallingRunTracker fallingRuns = new FallingRunTracker();
	private final LongOpenHashSet pendingChunks = new LongOpenHashSet();
	// Positions (BlockPos longs) in the scanned zones whose block changed since the last tick.
	private final LongOpenHashSet changedBlocks = new LongOpenHashSet();
	private int tickCounter = 0;
	private boolean dormantNoticePending = false;

	private ScanManager() {
		for (Zone zone : Zone.VALUES) {
			highlights.put(zone, new ChunkIndexedPositions());
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

	/** Highlighted blocks of the zone (BlockPos longs). Do not modify. */
	public LongOpenHashSet highlights(Zone zone) {
		return highlights.get(zone).positions();
	}

	/** Falling-block run highlights with runs along the X axis. Do not modify. */
	public LongOpenHashSet fallingAlongX() {
		return fallingRuns.alongX();
	}

	/** Falling-block run highlights with runs along the Z axis. Do not modify. */
	public LongOpenHashSet fallingAlongZ() {
		return fallingRuns.alongZ();
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
	 * rectangle) in the given dimension. Returns messages about config entries
	 * that could not be parsed or name tags this world does not have.
	 */
	public List<Component> activate(ResourceKey<Level> dim, ChunkPos a, ChunkPos b) {
		PeriScanConfig config = PeriScanConfig.get();
		if (!PeriScanConfig.anyZoneEnabled()) {
			// Nothing to scan; a later /peri scan reload starts from the saved profile.
			deactivate();
			return List.of();
		}
		this.dimension = dim;
		this.cornerA = a;
		this.cornerB = b;
		this.layout = ZoneLayout.of(a, b, ZoneLayout.Settings.from(config));

		List<String> invalidEntries = new ArrayList<>();
		ZoneMatcher.WaterloggedExclusions exclusions = ZoneMatcher.WaterloggedExclusions.compile(
				config.waterloggedBlacklist, config.waterloggedExcludePushDestroy, invalidEntries);
		matchers.clear();
		for (Zone zone : Zone.VALUES) {
			matchers.put(zone, zone.compileMatcher(config, layout, exclusions, invalidEntries));
		}
		List<Component> problems = new ArrayList<>();
		for (String entry : invalidEntries) {
			problems.add(Component.translatable("periscan.msg.invalid_entry", entry));
		}
		for (String entry : unknownTags(exclusions)) {
			problems.add(Component.translatable("periscan.msg.unknown_tag", entry));
		}

		clearScanResults();
		for (ChunkPos chunk : layout.chunks()) {
			pendingChunks.add(VersionCompat.chunkKey(chunk));
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
		return problems;
	}

	/**
	 * Config entries naming tags the world does not have (usually a typo, e.g.
	 * "#minecraft:wall" for "#minecraft:walls"): such tags match nothing.
	 */
	private List<String> unknownTags(ZoneMatcher.WaterloggedExclusions exclusions) {
		Map<TagKey<Block>, String> tags = new LinkedHashMap<>();
		exclusions.collectTags(tags);
		for (ZoneMatcher matcher : matchers.values()) {
			matcher.collectTags(tags);
		}
		// The server sends the world's tags on join, so only known tags are bound here.
		Registry<Block> blocks = BuiltInRegistries.BLOCK;
		List<String> unknown = new ArrayList<>();
		tags.forEach((tag, entry) -> {
			if (blocks.get(tag).isEmpty()) {
				unknown.add(entry);
			}
		});
		return unknown;
	}

	/** Re-derives zones/matchers from current config and rescans, keeping the region. */
	public List<Component> rescan() {
		if (!isActive()) {
			return List.of();
		}
		if (!PeriScanConfig.anyZoneEnabled()) {
			// All zones were just disabled: stop scanning (/peri scan reload starts it again).
			deactivate();
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				VersionCompat.sendChat(client.player, Component.translatable("periscan.msg.all_disabled"));
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

	private void clearScanResults() {
		for (ChunkIndexedPositions set : highlights.values()) {
			set.clear();
		}
		fallingRuns.clear();
		pendingChunks.clear();
		changedBlocks.clear();
	}

	private void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (layout == null || level.dimension() != dimension || !layout.intersectsChunk(chunk.getPos())) {
			return;
		}
		pendingChunks.remove(VersionCompat.chunkKey(chunk.getPos()));
		// Remove stale highlights from this chunk before rescanning it (covers
		// changes that happened while the chunk was unloaded).
		clearChunkHighlights(chunk.getPos());
		scanChunk(level, chunk);
		flushFallingLines(level);
	}

	/**
	 * Called for every block state change on the client (server updates and the
	 * player's own predicted changes; see ClientLevelMixin). Only remembers the
	 * position: a burst of changes (an explosion, a section update) is applied
	 * once at the end of the tick, from the block states as they are then.
	 */
	public void onBlockChanged(ClientLevel level, BlockPos pos) {
		if (layout == null || level.dimension() != dimension || !layout.containsColumn(pos.getX(), pos.getZ())) {
			return;
		}
		changedBlocks.add(pos.asLong());
	}

	/** Brings the highlights of the blocks changed this tick up to date. */
	private void applyBlockChanges(ClientLevel level) {
		if (changedBlocks.isEmpty()) {
			return;
		}
		int minY = scanMinY(level);
		int maxY = scanMaxY(level);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LongIterator it = changedBlocks.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			int x = BlockPos.getX(key);
			int y = BlockPos.getY(key);
			int z = BlockPos.getZ(key);
			// A chunk that is not scanned yet gets all its blocks when it is.
			if (pendingChunks.contains(VersionCompat.chunkKey(new ChunkPos(x >> 4, z >> 4)))
					|| !level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			if (y >= minY && y <= maxY) {
				updateHighlights(level, pos.set(x, y, z), minY);
			}
			fallingRuns.markPosition(layout.trenchStrips(), x, y, z, minY + BOTTOM_TRENCH_LAYERS, maxY);
		}
		changedBlocks.clear();
		flushFallingLines(level);
	}

	/** Re-evaluates a single block for every zone, like scanChunk does for a whole chunk. */
	private void updateHighlights(ClientLevel level, BlockPos pos, int minY) {
		long key = pos.asLong();
		for (ChunkIndexedPositions set : highlights.values()) {
			set.remove(key);
		}
		int mask = layout.zoneMask(pos.getX(), pos.getZ());
		BlockState state = level.getBlockState(pos);
		if (mask != 0 && !state.isAir()) {
			addMatchingZones(mask, pos.getX(), pos.getY(), pos.getZ(), state, minY + BOTTOM_TRENCH_LAYERS - 1);
		}
	}

	private void clearChunkHighlights(ChunkPos pos) {
		for (ChunkIndexedPositions set : highlights.values()) {
			set.removeChunk(VersionCompat.chunkX(pos), VersionCompat.chunkZ(pos));
		}
		fallingRuns.clearChunk(VersionCompat.chunkX(pos), VersionCompat.chunkZ(pos));
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
					addMatchingZones(mask, baseX + dx, y, baseZ + dz, state, bottomTopY);
				}
			}
		}

		// The lowest layers belong to the bottom trench zone, not the trench inner.
		fallingRuns.markChunk(layout.trenchStrips(), VersionCompat.chunkX(cp), VersionCompat.chunkZ(cp),
				minY + BOTTOM_TRENCH_LAYERS, maxY, blockView(level));
	}

	/**
	 * Highlights the block in every zone of the column mask whose matcher accepts it.
	 * The bottom trench zone is the lowest scanned layers (up to bottomTopY); the
	 * trench inner zone starts above them.
	 */
	private void addMatchingZones(int mask, int x, int y, int z, BlockState state, int bottomTopY) {
		for (Zone zone : Zone.VALUES) {
			if ((mask & zone.mask()) == 0) {
				continue;
			}
			if (zone == Zone.BOTTOM_TRENCH ? y > bottomTopY : zone == Zone.TRENCH_INNER && y <= bottomTopY) {
				continue;
			}
			if (matchers.get(zone).matches(state, x, z)) {
				highlights.get(zone).add(BlockPos.asLong(x, y, z));
			}
		}
	}

	private void flushFallingLines(ClientLevel level) {
		fallingRuns.flush(blockView(level), layout.region(), PeriScanConfig.get().trenchInner.fallingRunLength);
	}

	/**
	 * Classifies blocks for the falling-run tracker straight from chunk sections.
	 * Caches the last chunk, as the tracker walks lines block by block.
	 */
	private static FallingRunTracker.BlockView blockView(ClientLevel level) {
		return new FallingRunTracker.BlockView() {
			private int chunkX = Integer.MIN_VALUE;
			private int chunkZ = Integer.MIN_VALUE;
			private LevelChunk chunk;

			@Override
			public FallingRunTracker.RunBlock at(int x, int y, int z) {
				if (x >> 4 != chunkX || z >> 4 != chunkZ) {
					chunkX = x >> 4;
					chunkZ = z >> 4;
					chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				}
				if (chunk == null) {
					return null;
				}
				LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
				return section.hasOnlyAir() ? FallingRunTracker.RunBlock.SKIPS
						: FallingRunTracker.RunBlock.of(section.getBlockState(x & 15, y & 15, z & 15));
			}
		};
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
			VersionCompat.sendChat(client.player, Component.translatable("periscan.msg.region_available"));
		}
		if (layout == null || client.level.dimension() != dimension) {
			return;
		}
		tickCounter++;

		applyBlockChanges(client.level);

		// Safety net for the removal side: drop highlights whose block no longer
		// matches, in case a change bypassed onBlockChanged or the world's tags
		// changed. Block changes normally update the highlights right away.
		if (tickCounter % VALIDATE_INTERVAL_TICKS == 0) {
			validateHighlights(client.level);
		}

		if (!pendingChunks.isEmpty() && tickCounter % 20 == 0 && client.player != null) {
			VersionCompat.sendOverlay(client.player,
					Component.translatable("periscan.msg.pending_chunks", pendingChunks.size()));
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

	/** Re-checks highlighted falling blocks and recomputes the lines of replaced ones. */
	private void validateFallingHighlights(ClientLevel level) {
		fallingRuns.markChangedHighlights(blockView(level));
		flushFallingLines(level);
	}

}
