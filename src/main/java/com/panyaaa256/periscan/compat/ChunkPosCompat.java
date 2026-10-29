package com.panyaaa256.periscan.compat;

import net.minecraft.world.level.ChunkPos;

/**
 * ChunkPos calls whose shape differs between the Minecraft versions this branch
 * builds, for both sides (VersionCompat holds the client-only ones).
 */
public final class ChunkPosCompat {
	private ChunkPosCompat() {
	}

	/** Chunk X coordinate; ChunkPos became a record in 26.1. */
	public static int chunkX(ChunkPos pos) {
		//? if >=26.1 {
		return pos.x();
		//?} else
		//return pos.x;
	}

	/** Chunk Z coordinate; ChunkPos became a record in 26.1. */
	public static int chunkZ(ChunkPos pos) {
		//? if >=26.1 {
		return pos.z();
		//?} else
		//return pos.z;
	}

	/** The chunk position packed into a long, as used by ChunkPos.getX/getZ(long). */
	public static long chunkKey(ChunkPos pos) {
		//? if >=26.1 {
		return pos.pack();
		//?} else
		//return pos.toLong();
	}
}
