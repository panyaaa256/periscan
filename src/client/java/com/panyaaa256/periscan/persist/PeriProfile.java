package com.panyaaa256.periscan.persist;

import net.minecraft.world.level.ChunkPos;

import java.time.LocalDate;

/**
 * A named perimeter: its chunk range (normalized to min/max corners), the
 * dimension it was created in, and the creation date. Profiles are the unit
 * both scanning and schematic placement operate on.
 */
public record PeriProfile(String name, int minX, int minZ, int maxX, int maxZ, String dimension, String createdAt) {

	/** Creates a profile from two arbitrary corner chunks, normalizing them. */
	public static PeriProfile of(String name, ChunkPos a, ChunkPos b, String dimension) {
		return new PeriProfile(name,
				Math.min(a.x, b.x), Math.min(a.z, b.z),
				Math.max(a.x, b.x), Math.max(a.z, b.z),
				dimension, LocalDate.now().toString());
	}

	public ChunkPos minChunk() {
		return new ChunkPos(minX, minZ);
	}

	public ChunkPos maxChunk() {
		return new ChunkPos(maxX, maxZ);
	}

	public int sizeBlocksX() {
		return (maxX - minX + 1) * 16;
	}

	public int sizeBlocksZ() {
		return (maxZ - minZ + 1) * 16;
	}
}
