package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.PeriDimension;
import com.panyaaa256.periscan.zone.Zone;
import com.panyaaa256.periscan.zone.ZoneLayout;
import com.panyaaa256.periscan.zone.ZoneMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which blocks each zone highlights in one scan: the zone layout of the
 * perimeter and the matchers compiled from the scan settings. The client's own
 * scan and the server's both use it.
 */
public final class ZoneScanner {
	// Height of the bottom trench zone: the lowest scanned layers of the trench body.
	private static final int BOTTOM_TRENCH_LAYERS = 2;

	/** Receives the highlighted blocks (BlockPos longs) of each zone. */
	@FunctionalInterface
	public interface Sink {
		void add(Zone zone, long pos);
	}

	private final ZoneLayout layout;
	private final ScanSettings settings;
	private final ZoneMatcher.WaterloggedExclusions exclusions;
	private final EnumMap<Zone, ZoneMatcher> matchers = new EnumMap<>(Zone.class);
	// Layers at the bottom of the trench body that belong to the bottom trench zone
	// instead of the trench inner zone: none while the bottom trench zone is
	// disabled, so the trench inner zone then covers them too.
	private final int bottomTrenchLayers;

	/**
	 * Compiles the scan of the perimeter between the given corners (chunk
	 * coordinates, outermost rectangle). Unparsable config entries are appended
	 * to invalidEntries.
	 */
	public ZoneScanner(ChunkPos a, ChunkPos b, ScanSettings settings, List<String> invalidEntries) {
		this.settings = settings;
		this.layout = ZoneLayout.of(a, b, ZoneLayout.Settings.from(settings));
		this.bottomTrenchLayers = settings.bottomTrench() ? BOTTOM_TRENCH_LAYERS : 0;
		this.exclusions = ZoneMatcher.WaterloggedExclusions.compile(
				settings.waterloggedBlacklist(), settings.waterloggedExcludePushDestroy(), invalidEntries);
		for (Zone zone : Zone.VALUES) {
			matchers.put(zone, zone.compileMatcher(settings, layout, exclusions, invalidEntries));
		}
	}

	public ZoneLayout layout() {
		return layout;
	}

	public ZoneMatcher matcher(Zone zone) {
		return matchers.get(zone);
	}

	/** Number of lowest scanned layers belonging to the bottom trench zone (0 while it is disabled). */
	public int bottomTrenchLayers() {
		return bottomTrenchLayers;
	}

	/** Collects the tags named by the config entries, mapped to the entry naming them. */
	public void collectTags(Map<TagKey<Block>, String> tags) {
		exclusions.collectTags(tags);
		for (ZoneMatcher matcher : matchers.values()) {
			matcher.collectTags(tags);
		}
	}

	/** Lowest scanned Y (inclusive): the bedrock floor is skipped where it is known. */
	public static int minY(Level level) {
		PeriDimension dimension = PeriDimension.of(level.dimension());
		return dimension == null ? level.getMinY() : Math.max(level.getMinY(), dimension.scanFloorY());
	}

	/** Highest scanned Y (inclusive), from the settings. */
	public int maxY(Level level) {
		return Math.min(level.getMaxY(), settings.scanMaxY());
	}

	/**
	 * Highlights the blocks of a chunk between minY and maxY. The sections are
	 * the chunk's, lowest first, starting at section minSectionY; a missing
	 * (null) section counts as air. Returns false if no column of the chunk
	 * belongs to a zone, so nothing was scanned.
	 */
	public boolean scanChunk(int chunkX, int chunkZ, LevelChunkSection[] sections, int minSectionY,
			int minY, int maxY, Sink sink) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;

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
			return false;
		}

		int bottomTopY = minY + bottomTrenchLayers - 1;
		for (int y = minY; y <= maxY; y++) {
			LevelChunkSection section = sections[(y >> 4) - minSectionY];
			if (section == null || section.hasOnlyAir()) {
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
					addMatchingZones(mask, baseX + dx, y, baseZ + dz, state, bottomTopY, sink);
				}
			}
		}
		return true;
	}

	/** Highlights a single block like scanChunk does; minY is the lowest scanned Y. */
	public void scanBlock(int x, int y, int z, BlockState state, int minY, Sink sink) {
		int mask = layout.zoneMask(x, z);
		if (mask != 0 && !state.isAir()) {
			addMatchingZones(mask, x, y, z, state, minY + bottomTrenchLayers - 1, sink);
		}
	}

	/**
	 * Highlights the block in every zone of the column mask whose matcher accepts it.
	 * The bottom trench zone is the lowest scanned layers (up to bottomTopY); the
	 * trench inner zone starts above them. With the bottom trench zone disabled
	 * bottomTopY is below the scan, so the trench inner zone takes every layer.
	 */
	private void addMatchingZones(int mask, int x, int y, int z, BlockState state, int bottomTopY, Sink sink) {
		for (Zone zone : Zone.VALUES) {
			if ((mask & zone.mask()) == 0) {
				continue;
			}
			if (zone == Zone.BOTTOM_TRENCH ? y > bottomTopY : zone == Zone.TRENCH_INNER && y <= bottomTopY) {
				continue;
			}
			if (matchers.get(zone).matches(state, x, z)) {
				sink.add(zone, BlockPos.asLong(x, y, z));
			}
		}
	}
}
