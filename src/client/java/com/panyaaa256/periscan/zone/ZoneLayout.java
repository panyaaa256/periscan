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
 */
public final class ZoneLayout {

	public record Rect(int minX, int minZ, int maxX, int maxZ) {
		public boolean valid() {
			return minX <= maxX && minZ <= maxZ;
		}

		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}
	}

	public record ZoneRect(Zone zone, Rect rect) {
	}

	private final List<ZoneRect> rects = new ArrayList<>();
	// Bounds of everything that needs scanning (region plus the outer one-block ring if present).
	private final Rect scanBounds;

	private final boolean trenchActive;
	private final int regionMinX;
	private final int regionMinZ;
	private final int regionMaxX;
	private final int regionMaxZ;
	private final int ns;
	private final int ew;

	private ZoneLayout(Rect scanBounds, boolean trenchActive,
			int regionMinX, int regionMinZ, int regionMaxX, int regionMaxZ, int ns, int ew) {
		this.scanBounds = scanBounds;
		this.trenchActive = trenchActive;
		this.regionMinX = regionMinX;
		this.regionMinZ = regionMinZ;
		this.regionMaxX = regionMaxX;
		this.regionMaxZ = regionMaxZ;
		this.ns = ns;
		this.ew = ew;
	}

	public static ZoneLayout of(ChunkPos cornerA, ChunkPos cornerB, PeriScanConfig config) {
		int minX = Math.min(cornerA.x, cornerB.x) * 16;
		int minZ = Math.min(cornerA.z, cornerB.z) * 16;
		int maxX = Math.max(cornerA.x, cornerB.x) * 16 + 15;
		int maxZ = Math.max(cornerA.z, cornerB.z) * 16 + 15;

		int ns = config.northSouthWidth; // thickness along Z
		int ew = config.eastWestWidth;   // thickness along X

		if (!config.useQuarryLikeTrencher) {
			// No trench: the whole specified region is the eater area.
			ZoneLayout layout = new ZoneLayout(new Rect(minX, minZ, maxX, maxZ), false, minX, minZ, maxX, maxZ, ns, ew);
			layout.add(Zone.EATER, new Rect(minX, minZ, maxX, maxZ));
			return layout;
		}

		ZoneLayout layout = new ZoneLayout(new Rect(minX - 1, minZ - 1, maxX + 1, maxZ + 1), true, minX, minZ, maxX, maxZ, ns, ew);

		// Trench body: two Z-end strips (full X length) and two X-end strips (full Z length).
		layout.add(Zone.TRENCH_INNER, new Rect(minX, minZ, maxX, minZ + ns - 1));
		layout.add(Zone.TRENCH_INNER, new Rect(minX, maxZ - ns + 1, maxX, maxZ));
		layout.add(Zone.TRENCH_INNER, new Rect(minX, minZ, minX + ew - 1, maxZ));
		layout.add(Zone.TRENCH_INNER, new Rect(maxX - ew + 1, minZ, maxX, maxZ));

		// One-block lines on both sides of each strip, spanning the strip's full length.
		layout.add(Zone.TRENCH_OUTER, new Rect(minX, minZ - 1, maxX, minZ - 1));
		layout.add(Zone.TRENCH_OUTER, new Rect(minX, minZ + ns, maxX, minZ + ns));
		layout.add(Zone.TRENCH_OUTER, new Rect(minX, maxZ - ns, maxX, maxZ - ns));
		layout.add(Zone.TRENCH_OUTER, new Rect(minX, maxZ + 1, maxX, maxZ + 1));
		layout.add(Zone.TRENCH_OUTER, new Rect(minX - 1, minZ, minX - 1, maxZ));
		layout.add(Zone.TRENCH_OUTER, new Rect(minX + ew, minZ, minX + ew, maxZ));
		layout.add(Zone.TRENCH_OUTER, new Rect(maxX - ew, minZ, maxX - ew, maxZ));
		layout.add(Zone.TRENCH_OUTER, new Rect(maxX + 1, minZ, maxX + 1, maxZ));

		// Eater area: the interior with the trenches removed.
		layout.add(Zone.EATER, new Rect(minX + ew, minZ + ns, maxX - ew, maxZ - ns));

		return layout;
	}

	private void add(Zone zone, Rect rect) {
		if (rect.valid()) {
			rects.add(new ZoneRect(zone, rect));
		}
	}

	public Rect scanBounds() {
		return scanBounds;
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
		if (!trenchActive) {
			return false;
		}
		if (ns != 3) {
			if (z >= regionMinZ && z <= regionMinZ + ns - 1 && (z - regionMinZ + 1) % 3 == 2) {
				return true;
			}
			if (z <= regionMaxZ && z >= regionMaxZ - ns + 1 && (regionMaxZ - z + 1) % 3 == 2) {
				return true;
			}
		}
		if (ew != 3) {
			if (x >= regionMinX && x <= regionMinX + ew - 1 && (x - regionMinX + 1) % 3 == 2) {
				return true;
			}
			if (x <= regionMaxX && x >= regionMaxX - ew + 1 && (regionMaxX - x + 1) % 3 == 2) {
				return true;
			}
		}
		return false;
	}

	public boolean intersectsChunk(ChunkPos pos) {
		int minBX = pos.getMinBlockX();
		int minBZ = pos.getMinBlockZ();
		return minBX + 15 >= scanBounds.minX && minBX <= scanBounds.maxX
				&& minBZ + 15 >= scanBounds.minZ && minBZ <= scanBounds.maxZ;
	}

	/** All chunk positions that intersect the scan bounds. */
	public List<ChunkPos> chunks() {
		List<ChunkPos> result = new ArrayList<>();
		int minCX = scanBounds.minX >> 4;
		int maxCX = scanBounds.maxX >> 4;
		int minCZ = scanBounds.minZ >> 4;
		int maxCZ = scanBounds.maxZ >> 4;
		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				result.add(new ChunkPos(cx, cz));
			}
		}
		return result;
	}
}
