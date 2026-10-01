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

	private static Set<String> drain(ChunkIndexedPositions set) {
		Set<String> dirty = new java.util.TreeSet<>();
		set.drainDirty(key -> dirty.add(ChunkIndexedPositions.chunkX(key) + "," + ChunkIndexedPositions.chunkZ(key)));
		return dirty;
	}

	@Test
	void changeMarksOnlyItsChunkAndDrainForgetsIt() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(BlockPos.asLong(5, 64, -5));

		assertEquals(Set.of("0,-1"), drain(set));
		assertTrue(drain(set).isEmpty());
	}

	@Test
	void edgeChangeMarksPopulatedNeighboursOnly() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(BlockPos.asLong(16, 64, 5)); // chunk (1,0)
		set.add(BlockPos.asLong(0, 64, 16)); // chunk (0,1)
		drain(set);

		set.add(BlockPos.asLong(15, 64, 15)); // chunk (0,0), corner: touches (1,0), (0,1), (1,1)

		assertEquals(Set.of("0,0", "1,0", "0,1"), drain(set));
	}

	@Test
	void removeMarksTheChunkEvenWhenItBecomesEmpty() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(IN_CHUNK_0);
		drain(set);

		set.remove(IN_CHUNK_0);

		assertEquals(Set.of("0,0"), drain(set));
	}

	@Test
	void removeChunkAndClearAndRemoveIfMarkTheAffectedChunks() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(IN_CHUNK_0);
		set.add(IN_CHUNK_MINUS_1);
		drain(set);

		set.removeChunk(0, 0);
		assertEquals(Set.of("0,0", "-1,0"), drain(set));

		set.add(IN_CHUNK_0);
		drain(set);
		set.removeIf(pos -> pos == IN_CHUNK_0);
		assertEquals(Set.of("0,0"), drain(set));

		set.add(IN_CHUNK_0);
		drain(set);
		set.clear();
		assertEquals(Set.of("0,0", "-1,0"), drain(set));
	}

	@Test
	void unchangedAddOrRemoveMarksNothing() {
		ChunkIndexedPositions set = new ChunkIndexedPositions();
		set.add(IN_CHUNK_0);
		drain(set);

		set.add(IN_CHUNK_0);
		set.remove(OTHER_IN_CHUNK_0);

		assertTrue(drain(set).isEmpty());
	}
}
