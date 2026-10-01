package com.panyaaa256.periscan.scan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import net.minecraft.core.BlockPos;

/**
 * A set of block positions (BlockPos longs) that also knows which positions
 * each chunk holds, so dropping a chunk's positions does not walk the whole set.
 * A chunk is rescanned on every load, and walking every highlight each time
 * made loading a large region slow down as its highlights grew.
 *
 * <p>It also records which chunks changed since the renderer last looked, so
 * only those chunks' meshes are rebuilt. A change at a chunk's edge also marks
 * the neighbouring chunks that hold positions, because their meshes depend on
 * what sits next to them.
 */
public final class ChunkIndexedPositions {
	private final LongOpenHashSet positions = new LongOpenHashSet();
	private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();

	// Chunks (chunk keys) whose positions, or whose neighbours' positions, changed.
	private final LongOpenHashSet dirtyChunks = new LongOpenHashSet();

	/** All positions, for reading. Change them only through this class. */
	public LongOpenHashSet positions() {
		return positions;
	}

	/** The positions in the chunk, or null if it holds none. Do not modify. */
	public LongOpenHashSet chunkPositions(long chunkKey) {
		return byChunk.get(chunkKey);
	}

	/** Keys of the chunks that hold positions. Do not modify. */
	public LongSet chunkKeys() {
		return byChunk.keySet();
	}

	/**
	 * Passes every chunk key changed since the last call (including chunks that
	 * became empty) to {@code consumer}, then forgets them.
	 */
	public void drainDirty(LongConsumer consumer) {
		LongIterator it = dirtyChunks.iterator();
		while (it.hasNext()) {
			consumer.accept(it.nextLong());
		}
		dirtyChunks.clear();
	}

	/** Forgets the recorded changes, for a consumer that is about to read everything. */
	public void discardDirty() {
		dirtyChunks.clear();
	}

	public static int chunkX(long chunkKey) {
		return (int) (chunkKey >> 32);
	}

	public static int chunkZ(long chunkKey) {
		return (int) chunkKey;
	}

	void add(long pos) {
		if (positions.add(pos)) {
			byChunk.computeIfAbsent(chunkKey(pos), key -> new LongOpenHashSet()).add(pos);
			markDirty(pos);
		}
	}

	void remove(long pos) {
		if (positions.remove(pos)) {
			removeFromChunk(pos);
			markDirty(pos);
		}
	}

	/** Removes the positions the predicate accepts. */
	void removeIf(LongPredicate predicate) {
		LongIterator it = positions.iterator();
		while (it.hasNext()) {
			long pos = it.nextLong();
			if (predicate.test(pos)) {
				it.remove();
				removeFromChunk(pos);
				markDirty(pos);
			}
		}
	}

	/** Removes every position in the chunk. */
	void removeChunk(int chunkX, int chunkZ) {
		removeChunk(chunkX, chunkZ, pos -> {
		});
	}

	/** Removes every position in the chunk, passing each one to {@code removed}. */
	void removeChunk(int chunkX, int chunkZ, LongConsumer removed) {
		long key = chunkKey(chunkX, chunkZ);
		LongOpenHashSet inChunk = byChunk.remove(key);
		if (inChunk == null) {
			return;
		}
		markChunkDirty(chunkX, chunkZ, true);
		LongIterator it = inChunk.iterator();
		while (it.hasNext()) {
			long pos = it.nextLong();
			positions.remove(pos);
			removed.accept(pos);
		}
	}

	void clear() {
		dirtyChunks.addAll(byChunk.keySet());
		positions.clear();
		byChunk.clear();
	}

	/** Marks the position's chunk, and the neighbours its edge position can affect, as changed. */
	private void markDirty(long pos) {
		int x = BlockPos.getX(pos);
		int z = BlockPos.getZ(pos);
		int localX = x & 15;
		int localZ = z & 15;
		int dx = localX == 0 ? -1 : localX == 15 ? 1 : 0;
		int dz = localZ == 0 ? -1 : localZ == 15 ? 1 : 0;
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		dirtyChunks.add(chunkKey(chunkX, chunkZ));
		if (dx != 0) {
			markIfPopulated(chunkX + dx, chunkZ);
		}
		if (dz != 0) {
			markIfPopulated(chunkX, chunkZ + dz);
		}
		if (dx != 0 && dz != 0) {
			markIfPopulated(chunkX + dx, chunkZ + dz);
		}
	}

	/** Marks a chunk that was emptied or removed: itself, and every populated neighbour. */
	private void markChunkDirty(int chunkX, int chunkZ, boolean withNeighbours) {
		dirtyChunks.add(chunkKey(chunkX, chunkZ));
		if (withNeighbours) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0) {
						markIfPopulated(chunkX + dx, chunkZ + dz);
					}
				}
			}
		}
	}

	private void markIfPopulated(int chunkX, int chunkZ) {
		long key = chunkKey(chunkX, chunkZ);
		if (byChunk.containsKey(key)) {
			dirtyChunks.add(key);
		}
	}

	private void removeFromChunk(long pos) {
		long chunk = chunkKey(pos);
		LongOpenHashSet inChunk = byChunk.get(chunk);
		if (inChunk != null && inChunk.remove(pos) && inChunk.isEmpty()) {
			byChunk.remove(chunk);
		}
	}

	private static long chunkKey(long pos) {
		return chunkKey(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
