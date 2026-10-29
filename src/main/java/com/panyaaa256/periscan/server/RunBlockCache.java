package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.scan.FallingRunTracker.BlockView;
import com.panyaaa256.periscan.scan.FallingRunTracker.RunBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * How each block of the scanned chunks takes part in falling-block runs, kept
 * by the server for the chunks the trench strips cross. Runs span the whole
 * region and most of its chunks are not loaded (they were read from the save),
 * so the lines are recomputed from this instead of from the world. Two bits per
 * block between minY and maxY: about 12 KB per chunk at the default height.
 */
final class RunBlockCache implements BlockView {
	private static final RunBlock[] RUN_BLOCKS = RunBlock.values();
	private static final int BITS = 2;

	private final int minY;
	private final int maxY;
	// Chunk key (as in chunkKey below) to the packed run blocks of the chunk.
	private final Long2ObjectOpenHashMap<long[]> chunks = new Long2ObjectOpenHashMap<>();
	// The chunk at() looked up last: a recomputed line walks it block by block.
	private long lastKey = Long.MIN_VALUE;
	private long[] lastPacked;

	RunBlockCache(int minY, int maxY) {
		this.minY = minY;
		this.maxY = maxY;
	}

	/**
	 * Remembers the run blocks of a scanned chunk, given as its sections (lowest
	 * first, starting at section minSectionY; a null section is air).
	 */
	void storeChunk(int chunkX, int chunkZ, LevelChunkSection[] sections, int minSectionY) {
		long[] packed = new long[(256 * (maxY - minY + 1) * BITS + 63) / 64];
		chunks.put(chunkKey(chunkX, chunkZ), packed);
		lastKey = Long.MIN_VALUE;
		for (int y = minY; y <= maxY; y++) {
			LevelChunkSection section = sections[(y >> 4) - minSectionY];
			boolean air = section == null || section.hasOnlyAir();
			for (int localX = 0; localX < 16; localX++) {
				for (int localZ = 0; localZ < 16; localZ++) {
					RunBlock block = air ? RunBlock.SKIPS
							: RunBlock.of(section.getBlockState(localX, y & 15, localZ));
					set(packed, index(localX, y, localZ), block);
				}
			}
		}
	}

	/** Updates a changed block of a stored chunk; other positions are ignored. */
	void update(int x, int y, int z, BlockState state) {
		long[] packed = chunks.get(chunkKey(x >> 4, z >> 4));
		if (packed != null && y >= minY && y <= maxY) {
			set(packed, index(x & 15, y, z & 15), RunBlock.of(state));
		}
	}

	/** The run block at the position, or null if its chunk is not stored (not scanned yet). */
	@Override
	public RunBlock at(int x, int y, int z) {
		long key = chunkKey(x >> 4, z >> 4);
		if (key != lastKey) {
			lastKey = key;
			lastPacked = chunks.get(key);
		}
		long[] packed = lastPacked;
		if (packed == null || y < minY || y > maxY) {
			return null;
		}
		int bit = index(x & 15, y, z & 15) * BITS;
		return RUN_BLOCKS[(int) (packed[bit >> 6] >>> (bit & 63)) & 3];
	}

	private int index(int localX, int y, int localZ) {
		return ((y - minY) << 8) | (localZ << 4) | localX;
	}

	private static void set(long[] packed, int index, RunBlock block) {
		int bit = index * BITS;
		packed[bit >> 6] = packed[bit >> 6] & ~(3L << (bit & 63)) | ((long) block.ordinal() << (bit & 63));
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
