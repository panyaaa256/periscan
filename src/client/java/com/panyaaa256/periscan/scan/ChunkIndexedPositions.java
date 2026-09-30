package com.panyaaa256.periscan.scan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import net.minecraft.core.BlockPos;

/**
 * A set of block positions (BlockPos longs) that also knows which positions
 * each chunk holds, so dropping a chunk's positions does not walk the whole set.
 * A chunk is rescanned on every load, and walking every highlight each time
 * made loading a large region slow down as its highlights grew.
 */
final class ChunkIndexedPositions {
	private final LongOpenHashSet positions = new LongOpenHashSet();
	private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();

	/** All positions, for reading. Change them only through this class. */
	LongOpenHashSet positions() {
		return positions;
	}

	void add(long pos) {
		if (positions.add(pos)) {
			byChunk.computeIfAbsent(chunkKey(pos), key -> new LongOpenHashSet()).add(pos);
		}
	}

	void remove(long pos) {
		if (positions.remove(pos)) {
			removeFromChunk(pos);
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
		LongOpenHashSet inChunk = byChunk.remove(chunkKey(chunkX, chunkZ));
		if (inChunk == null) {
			return;
		}
		LongIterator it = inChunk.iterator();
		while (it.hasNext()) {
			long pos = it.nextLong();
			positions.remove(pos);
			removed.accept(pos);
		}
	}

	void clear() {
		positions.clear();
		byChunk.clear();
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
