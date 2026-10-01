package com.panyaaa256.periscan.zone;

import com.panyaaa256.periscan.config.PeriScanConfig;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the zone rectangles (in block coordinates, XZ plane, bounds inclusive)
 * from the outermost perimeter rectangle given as chunk coordinates.
 *
 * The command coordinates denote the outermost edge of the perimeter. The trench
 * strips are derived inward from it: north-south width (config, along Z) at both
 * Z ends, east-west width (config, along X) at both X ends. The "one outside the
 * trench" zone is the single-column line on both sides of each strip, which extends
 * one block beyond the specified region on the outside.
 *
 * If the region is too narrow along an axis to hold two opposite strips (size
 * &lt; 2 * width), it is assumed to contain a single trench on that axis, anchored
 * at the edge farther from the world origin (the outer side of a real perimeter).
 *
 * Each zone can be disabled in the config; disabled zones contribute no rects.
 * The eater zone either covers the whole specified region (trenches included) or
 * only the interior with the trench strips removed, per config.
 */
public final class ZoneLayout {

	public record Rect(int minX, int minZ, int maxX, int maxZ) {
		public boolean valid() {
			return minX <= maxX && minZ <= maxZ;
		}

		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		public boolean intersectsChunk(int chunkMinX, int chunkMinZ) {
			return chunkMinX + 15 >= minX && chunkMinX <= maxX
					&& chunkMinZ + 15 >= minZ && chunkMinZ <= maxZ;
		}
	}

	public record ZoneRect(Zone zone, Rect rect) {
	}

	/**
	 * The config values the layout depends on. Kept separate from PeriScanConfig
	 * so layouts can be built without loading the config (e.g. in tests).
	 *
	 * @param northSouthWidth trench thickness along Z
	 * @param eastWestWidth   trench thickness along X
	 */
	public record Settings(int northSouthWidth, int eastWestWidth,
			boolean trenchOuter, boolean trenchInner, boolean bottomTrench,
			boolean eater, boolean eaterIncludeTrench) {

		public static Settings from(PeriScanConfig config) {
			return new Settings(config.northSouthWidth, config.eastWestWidth,
					config.trenchOuter.enabled, config.trenchInner.enabled, config.bottomTrench.enabled,
					config.eater.enabled, config.eater.includeTrench);
		}
	}

	/**
	 * One trench body strip. {@code alongX} is the trencher's direction of travel
	 * (the strip's long axis): strips at the Z ends run along X and vice versa.
	 */
	public record TrenchStrip(Rect rect, boolean alongX) {
	}

	/**
	 * A trench strip with what the fence lane rule needs: which edge of the region
	 * it is anchored at (columns are counted from that outer edge) and its width.
	 * The strip's rect is already cut to the region, so a narrow region needs no
	 * special case when counting.
	 */
	private record Band(TrenchStrip strip, boolean fromMin, int width) {

		boolean laneMatters(int x, int z) {
			// A width-3 trench survives a wall/fence even in the middle column.
			if (width == 3 || !strip.rect().contains(x, z)) {
				return false;
			}
			Rect rect = strip.rect();
			int index = strip.alongX()
					? (fromMin ? z - rect.minZ() : rect.maxZ() - z)
					: (fromMin ? x - rect.minX() : rect.maxX() - x);
			// 1-based from the outer edge.
			return (index + 1) % 3 == 2;
		}
	}

	private final List<ZoneRect> rects = new ArrayList<>();
	// Only filled when the trench inner zone is enabled, so the fence lane rule is
	// off together with the zone.
	private final List<Band> bands = new ArrayList<>();
	private final List<TrenchStrip> trenchStrips = new ArrayList<>();
	// Bounds of everything that needs scanning (union of all enabled zone rects).
	private Rect scanBounds;
	private final Rect region;

	private ZoneLayout(Rect region) {
		this.region = region;
	}

	public static ZoneLayout of(ChunkPos cornerA, ChunkPos cornerB, Settings settings) {
		int minX = Math.min(cornerA.getMinBlockX(), cornerB.getMinBlockX());
		int minZ = Math.min(cornerA.getMinBlockZ(), cornerB.getMinBlockZ());
		int maxX = Math.max(cornerA.getMaxBlockX(), cornerB.getMaxBlockX());
		int maxZ = Math.max(cornerA.getMaxBlockZ(), cornerB.getMaxBlockZ());

		int ns = settings.northSouthWidth(); // thickness along Z
		int ew = settings.eastWestWidth();   // thickness along X

		// A region too narrow for two opposite strips holds a single trench, anchored
		// at the edge farther from the origin (ties go to the max side).
		boolean twoZ = maxZ - minZ + 1 >= 2 * ns;
		boolean stripZMax = twoZ || Math.abs(maxZ) >= Math.abs(minZ);
		boolean stripZMin = twoZ || !stripZMax;
		boolean twoX = maxX - minX + 1 >= 2 * ew;
		boolean stripXMax = twoX || Math.abs(maxX) >= Math.abs(minX);
		boolean stripXMin = twoX || !stripXMax;

		ZoneLayout layout = new ZoneLayout(new Rect(minX, minZ, maxX, maxZ));

		boolean inner = settings.trenchInner();
		boolean outer = settings.trenchOuter();
		boolean bottom = settings.bottomTrench();

		// Trench body strips (Z ends span the full X length, X ends the full Z length),
		// each with its one-block "outside the trench" lines on both sides. The bottom
		// trench zone shares the strip footprint (its Y range is cut in the scan).
		if (stripZMin) {
			layout.addStrip(new Rect(minX, minZ, maxX, Math.min(minZ + ns - 1, maxZ)), true, true, ns, inner, bottom);
			if (outer) {
				layout.add(Zone.TRENCH_OUTER, new Rect(minX, minZ - 1, maxX, minZ - 1));
				layout.add(Zone.TRENCH_OUTER, new Rect(minX, minZ + ns, maxX, minZ + ns));
			}
		}
		if (stripZMax) {
			layout.addStrip(new Rect(minX, Math.max(maxZ - ns + 1, minZ), maxX, maxZ), true, false, ns, inner, bottom);
			if (outer) {
				layout.add(Zone.TRENCH_OUTER, new Rect(minX, maxZ - ns, maxX, maxZ - ns));
				layout.add(Zone.TRENCH_OUTER, new Rect(minX, maxZ + 1, maxX, maxZ + 1));
			}
		}
		if (stripXMin) {
			layout.addStrip(new Rect(minX, minZ, Math.min(minX + ew - 1, maxX), maxZ), false, true, ew, inner, bottom);
			if (outer) {
				layout.add(Zone.TRENCH_OUTER, new Rect(minX - 1, minZ, minX - 1, maxZ));
				layout.add(Zone.TRENCH_OUTER, new Rect(minX + ew, minZ, minX + ew, maxZ));
			}
		}
		if (stripXMax) {
			layout.addStrip(new Rect(Math.max(maxX - ew + 1, minX), minZ, maxX, maxZ), false, false, ew, inner, bottom);
			if (outer) {
				layout.add(Zone.TRENCH_OUTER, new Rect(maxX - ew, minZ, maxX - ew, maxZ));
				layout.add(Zone.TRENCH_OUTER, new Rect(maxX + 1, minZ, maxX + 1, maxZ));
			}
		}

		if (settings.eater()) {
			if (settings.eaterIncludeTrench()) {
				layout.add(Zone.EATER, new Rect(minX, minZ, maxX, maxZ));
			} else {
				// The interior with the trenches removed (regardless of whether the
				// trench zones themselves are enabled).
				layout.add(Zone.EATER, new Rect(
						stripXMin ? minX + ew : minX, stripZMin ? minZ + ns : minZ,
						stripXMax ? maxX - ew : maxX, stripZMax ? maxZ - ns : maxZ));
			}
		}

		layout.scanBounds = layout.unionRects();
		return layout;
	}

	private Rect unionRects() {
		if (rects.isEmpty()) {
			return new Rect(0, 0, -1, -1);
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (ZoneRect zr : rects) {
			minX = Math.min(minX, zr.rect.minX);
			minZ = Math.min(minZ, zr.rect.minZ);
			maxX = Math.max(maxX, zr.rect.maxX);
			maxZ = Math.max(maxZ, zr.rect.maxZ);
		}
		return new Rect(minX, minZ, maxX, maxZ);
	}

	private void addStrip(Rect rect, boolean alongX, boolean fromMin, int width, boolean inner, boolean bottom) {
		if (!rect.valid()) {
			return;
		}
		if (inner) {
			add(Zone.TRENCH_INNER, rect);
			TrenchStrip strip = new TrenchStrip(rect, alongX);
			trenchStrips.add(strip);
			bands.add(new Band(strip, fromMin, width));
		}
		if (bottom) {
			add(Zone.BOTTOM_TRENCH, rect);
		}
	}

	private void add(Zone zone, Rect rect) {
		if (rect.valid()) {
			rects.add(new ZoneRect(zone, rect));
		}
	}

	public Rect scanBounds() {
		return scanBounds;
	}

	/**
	 * Whether the column is worth watching for block changes: inside the bounds of
	 * the enabled zones. That reaches one block past the region where the "outside
	 * the trench" lines are, and is empty when no zone is enabled.
	 */
	public boolean containsColumn(int x, int z) {
		return scanBounds.contains(x, z);
	}

	/** The trench body strips, empty when the trench inner zone is disabled. */
	public List<TrenchStrip> trenchStrips() {
		return trenchStrips;
	}

	/** The specified region (outermost perimeter rectangle) in block coordinates. */
	public Rect region() {
		return region;
	}

	/** Bitmask of zones (Zone#mask) that contain the given column. */
	public int zoneMask(int x, int z) {
		int mask = 0;
		for (ZoneRect zr : rects) {
			if (zr.rect.contains(x, z)) {
				mask |= zr.zone.mask();
			}
		}
		return mask;
	}

	/**
	 * Whether walls/fences at this trench column actually obstruct the trencher.
	 * Columns are counted 1-based from the perimeter's outer edge within each trench
	 * strip; only columns with index % 3 == 2 (2, 5, 8, 11, ...) matter, the machine
	 * ignores the rest. A width-3 trench is a special case: its trencher survives a
	 * wall/fence even in the middle column, so nothing matters there. At corners
	 * (overlap of two strips) qualifying in either strip counts. Only meaningful for
	 * positions inside the trench body.
	 */
	public boolean fenceLaneMatters(int x, int z) {
		for (Band band : bands) {
			if (band.laneMatters(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Whether any enabled zone rect intersects the given chunk. */
	public boolean intersectsChunk(ChunkPos pos) {
		int minBX = pos.getMinBlockX();
		int minBZ = pos.getMinBlockZ();
		if (!scanBounds.valid() || !scanBounds.intersectsChunk(minBX, minBZ)) {
			return false;
		}
		for (ZoneRect zr : rects) {
			if (zr.rect.intersectsChunk(minBX, minBZ)) {
				return true;
			}
		}
		return false;
	}

	/** All chunk positions that intersect some enabled zone rect. */
	public List<ChunkPos> chunks() {
		List<ChunkPos> result = new ArrayList<>();
		if (!scanBounds.valid()) {
			return result;
		}
		int minCX = scanBounds.minX >> 4;
		int maxCX = scanBounds.maxX >> 4;
		int minCZ = scanBounds.minZ >> 4;
		int maxCZ = scanBounds.maxZ >> 4;
		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				if (intersectsChunk(pos)) {
					result.add(pos);
				}
			}
		}
		return result;
	}
}
