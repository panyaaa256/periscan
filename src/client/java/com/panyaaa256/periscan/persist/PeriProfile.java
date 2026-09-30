package com.panyaaa256.periscan.persist;

import com.panyaaa256.periscan.compat.VersionCompat;
import net.minecraft.world.level.ChunkPos;

import java.time.LocalDate;

/**
 * A named perimeter: its chunk range (normalized to min/max corners), the
 * dimension it was created in, and the creation date. Profiles are the unit
 * both scanning and schematic placement operate on.
 */
public record PeriProfile(String name, int minX, int minZ, int maxX, int maxZ, String dimension, String createdAt) {
	/**
	 * Longest supported side of a perimeter, in chunks. Every chunk of the region
	 * is tracked until it is scanned, so a mistyped coordinate would otherwise
	 * freeze the game or run it out of memory.
	 */
	public static final int MAX_SIDE_CHUNKS = 512;

	/** Creates a profile from two arbitrary corner chunks, normalizing them. */
	public static PeriProfile of(String name, ChunkPos a, ChunkPos b, String dimension) {
		return new PeriProfile(name,
				Math.min(VersionCompat.chunkX(a), VersionCompat.chunkX(b)),
				Math.min(VersionCompat.chunkZ(a), VersionCompat.chunkZ(b)),
				Math.max(VersionCompat.chunkX(a), VersionCompat.chunkX(b)),
				Math.max(VersionCompat.chunkZ(a), VersionCompat.chunkZ(b)),
				dimension, LocalDate.now().toString());
	}

	public ChunkPos minChunk() {
		return new ChunkPos(minX, minZ);
	}

	public ChunkPos maxChunk() {
		return new ChunkPos(maxX, maxZ);
	}

	// Block coordinates of the outermost corner columns (bounds inclusive).

	public int minBlockX() {
		return minX * 16;
	}

	public int minBlockZ() {
		return minZ * 16;
	}

	public int maxBlockX() {
		return maxX * 16 + 15;
	}

	public int maxBlockZ() {
		return maxZ * 16 + 15;
	}

	public int sizeBlocksX() {
		return (maxX - minX + 1) * 16;
	}

	public int sizeBlocksZ() {
		return (maxZ - minZ + 1) * 16;
	}

	// Side lengths in chunks. long, as profiles read from disk may hold any int.

	public long sideChunksX() {
		return (long) maxX - minX + 1;
	}

	public long sideChunksZ() {
		return (long) maxZ - minZ + 1;
	}

	/** Whether a side is longer than {@link #MAX_SIDE_CHUNKS}. */
	public boolean exceedsMaxSize() {
		return sideChunksX() > MAX_SIDE_CHUNKS || sideChunksZ() > MAX_SIDE_CHUNKS;
	}
}
