package com.panyaaa256.periscan.scan;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkIndexedPositionsTest {
	private static final long IN_CHUNK_0 = BlockPos.asLong(15, 64, 0);
	private static final long IN_CHUNK_MINUS_1 = BlockPos.asLong(-1, 64, 0);
	private static final long OTHER_IN_CHUNK_0 = BlockPos.asLong(0, 70, 15);

	private static List<Long> removeChunk(ChunkIndexedPositions set, int chunkX, int chunkZ) {
		LongArrayList removed = new LongArrayList();
		set.removeChunk(chunkX, chunkZ, removed::add);
		return removed;
	}

	@Test
	void removeChunkDropsOnlyThatChunk() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(IN_CHUNK_0);
		set.add(IN_CHUNK_MINUS_1);
		set.add(OTHER_IN_CHUNK_0);

		// x = -1 belongs to chunk -1, not chunk 0.
		assertEquals(Set.of(IN_CHUNK_0, OTHER_IN_CHUNK_0), Set.copyOf(removeChunk(set, 0, 0)));
		assertEquals(Set.of(IN_CHUNK_MINUS_1), Set.copyOf(set.positions()));
	}

	@Test
	void removedPositionsAreNotReportedAgain() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(IN_CHUNK_0);
		set.add(OTHER_IN_CHUNK_0);
		set.remove(IN_CHUNK_0);
		set.removeIf(pos -> pos == OTHER_IN_CHUNK_0);

		assertTrue(removeChunk(set, 0, 0).isEmpty());
	}

	@Test
	void addingTwiceIsReportedOnce() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(IN_CHUNK_0);
		set.add(IN_CHUNK_0);

		assertEquals(List.of(IN_CHUNK_0), removeChunk(set, 0, 0));
		assertTrue(set.positions().isEmpty());
	}
}
