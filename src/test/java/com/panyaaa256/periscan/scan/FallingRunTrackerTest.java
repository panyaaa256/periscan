package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.scan.FallingRunTracker.BlockView;
import com.panyaaa256.periscan.scan.FallingRunTracker.RunBlock;
import com.panyaaa256.periscan.zone.ZoneLayout;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs are checked on single lines: along X at z = 5, y = 10 unless stated.
 * Unlisted blocks are air (skipped).
 */
class FallingRunTrackerTest {
	private static final int Y = 10;
	private static final int Z = 5;
	/** One chunk (the smallest region): blocks 0..15. */
	private static final ZoneLayout.Rect ONE_CHUNK = new ZoneLayout.Rect(0, 0, 15, 15);
	/** Three chunks along X: blocks x 0..47. */
	private static final ZoneLayout.Rect THREE_CHUNKS = new ZoneLayout.Rect(0, 0, 47, 15);

	/** A fake world: explicit blocks, air elsewhere, and a set of unloaded chunks. */
	private static final class FakeWorld implements BlockView {
		final Map<Long, RunBlock> blocks = new HashMap<>();
		final Set<Long> unloadedChunks = new HashSet<>();

		FakeWorld set(int x, int y, int z, RunBlock block) {
			blocks.put(BlockPos.asLong(x, y, z), block);
			return this;
		}

		/** Sets blocks x = fromX..toX on the default line. */
		FakeWorld row(int fromX, int toX, RunBlock block) {
			for (int x = fromX; x <= toX; x++) {
				set(x, Y, Z, block);
			}
			return this;
		}

		FakeWorld unload(int chunkX, int chunkZ) {
			unloadedChunks.add(chunkKey(chunkX, chunkZ));
			return this;
		}

		FakeWorld load(int chunkX, int chunkZ) {
			unloadedChunks.remove(chunkKey(chunkX, chunkZ));
			return this;
		}

		private static long chunkKey(int chunkX, int chunkZ) {
			return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
		}

		@Override
		public RunBlock at(int x, int y, int z) {
			if (unloadedChunks.contains(chunkKey(x >> 4, z >> 4))) {
				return null;
			}
			return blocks.getOrDefault(BlockPos.asLong(x, y, z), RunBlock.SKIPS);
		}
	}

	/** Marks the default X line dirty and recomputes it. */
	private static void recompute(FallingRunTracker tracker, FakeWorld world, ZoneLayout.Rect region, int threshold) {
		tracker.markChunk(List.of(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(region.minX(), Z, region.maxX(), Z), true)),
				0, 0, Y, Y, (x, y, z) -> RunBlock.SKIPS);
		tracker.flush(world, region, threshold);
	}

	private static Set<Integer> highlightedX(FallingRunTracker tracker) {
		Set<Integer> xs = new HashSet<>();
		tracker.alongX().forEach(key -> {
			assertEquals(Y, BlockPos.getY(key));
			assertEquals(Z, BlockPos.getZ(key));
			xs.add(BlockPos.getX(key));
		});
		return xs;
	}

	@Nested
	class Runs {
		@Test
		void runReachingTheThresholdIsHighlighted() {
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, new FakeWorld().row(2, 4, RunBlock.COUNTS), ONE_CHUNK, 3);
			assertEquals(Set.of(2, 3, 4), highlightedX(tracker));
		}

		@Test
		void runBelowTheThresholdIsNot() {
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, new FakeWorld().row(2, 4, RunBlock.COUNTS), ONE_CHUNK, 4);
			assertTrue(tracker.alongX().isEmpty());
		}

		@Test
		void skippedBlocksBridgeTheRunWithoutCounting() {
			// 2 sand, 3 air, 4 liquid, 5-6 sand: three falling blocks in one run.
			FakeWorld world = new FakeWorld().row(2, 2, RunBlock.COUNTS).row(4, 4, RunBlock.SKIPS).row(5, 6, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, ONE_CHUNK, 3);
			assertEquals(Set.of(2, 5, 6), highlightedX(tracker));
		}

		@Test
		void breakingBlockSplitsTheRun() {
			FakeWorld world = new FakeWorld().row(2, 3, RunBlock.COUNTS).row(4, 4, RunBlock.BREAKS).row(5, 5, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, ONE_CHUNK, 3);
			assertTrue(tracker.alongX().isEmpty());
		}

		@Test
		void runAtTheRegionEdgeCounts() {
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, new FakeWorld().row(13, 15, RunBlock.COUNTS), ONE_CHUNK, 3);
			assertEquals(Set.of(13, 14, 15), highlightedX(tracker));
		}

		@Test
		void blocksOutsideTheRegionAreIgnored() {
			// x = 16 is past the one-chunk region, so only two falling blocks remain.
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, new FakeWorld().row(14, 16, RunBlock.COUNTS), ONE_CHUNK, 3);
			assertTrue(tracker.alongX().isEmpty());
		}

		@Test
		void runsSpanChunks() {
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, new FakeWorld().row(14, 17, RunBlock.COUNTS), THREE_CHUNKS, 4);
			assertEquals(Set.of(14, 15, 16, 17), highlightedX(tracker));
		}

		@Test
		void thresholdBelowOneIsTreatedAsOne() {
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, new FakeWorld().row(7, 7, RunBlock.COUNTS), ONE_CHUNK, 0);
			assertEquals(Set.of(7), highlightedX(tracker));
		}

		@Test
		void runsAlongZAreKeptSeparately() {
			FakeWorld world = new FakeWorld();
			for (int z = 2; z <= 4; z++) {
				world.set(Z, Y, z, RunBlock.COUNTS);
			}
			FallingRunTracker tracker = new FallingRunTracker();
			tracker.markChunk(List.of(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(Z, 0, Z, 15), false)),
					0, 0, Y, Y, world);
			tracker.flush(world, ONE_CHUNK, 3);
			assertTrue(tracker.alongX().isEmpty());
			assertEquals(3, tracker.alongZ().size());
			assertTrue(tracker.alongZ().contains(BlockPos.asLong(Z, Y, 3)));
		}
	}

	@Nested
	class Updates {
		@Test
		void replacedBlockShrinksTheRunAndDropsItsHighlights() {
			FakeWorld world = new FakeWorld().row(2, 4, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, ONE_CHUNK, 3);
			world.row(3, 3, RunBlock.BREAKS);
			tracker.markChangedHighlights(world);
			tracker.flush(world, ONE_CHUNK, 3);
			assertTrue(tracker.alongX().isEmpty());
		}

		@Test
		void unchangedHighlightsMarkNothing() {
			FakeWorld world = new FakeWorld().row(2, 4, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, ONE_CHUNK, 3);
			tracker.markChangedHighlights(world);
			assertFalse(tracker.isLineDirty(true, Z, Y));
		}

		@Test
		void unloadedChunkKeepsCachedHighlightsAndEndsTheRun() {
			// Sand x 14..20 spans chunks 0 and 1 and is highlighted while loaded.
			FakeWorld world = new FakeWorld().row(14, 20, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, THREE_CHUNKS, 3);
			assertEquals(Set.of(14, 15, 16, 17, 18, 19, 20), highlightedX(tracker));

			// Chunk 1 unloads, then x = 14 is replaced: only x = 15 remains on the
			// loaded side (too short); the cached part in chunk 1 is kept.
			world.unload(1, 0).row(14, 14, RunBlock.BREAKS);
			tracker.markChangedHighlights(world);
			tracker.flush(world, THREE_CHUNKS, 3);
			assertEquals(Set.of(16, 17, 18, 19, 20), highlightedX(tracker));
		}

		@Test
		void runsDoNotJoinAcrossAnUnloadedChunk() {
			// Two falling blocks in chunk 0 and one in chunk 2, chunk 1 unknown.
			FakeWorld world = new FakeWorld().row(14, 15, RunBlock.COUNTS).row(32, 32, RunBlock.COUNTS).unload(1, 0);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, THREE_CHUNKS, 3);
			assertTrue(tracker.alongX().isEmpty());
		}

		@Test
		void clearingARescannedChunkReevaluatesItsNeighbors() {
			// Run x 14..17 across chunks 0 and 1. While chunk 0 was unloaded, its part
			// was replaced; on reload the remaining two blocks in chunk 1 are too short.
			FakeWorld world = new FakeWorld().row(14, 17, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, THREE_CHUNKS, 3);
			world.row(14, 15, RunBlock.BREAKS);
			tracker.clearChunk(0, 0);
			assertTrue(tracker.isLineDirty(true, Z, Y));
			tracker.flush(world, THREE_CHUNKS, 3);
			assertTrue(tracker.alongX().isEmpty());
		}

		@Test
		void clearChunkOnlyDropsThatChunk() {
			FakeWorld world = new FakeWorld().row(14, 17, RunBlock.COUNTS);
			FallingRunTracker tracker = new FallingRunTracker();
			recompute(tracker, world, THREE_CHUNKS, 3);
			tracker.clearChunk(0, 0);
			assertEquals(Set.of(16, 17), highlightedX(tracker));
		}
	}

	@Nested
	class MarkChunk {
		// One strip along X covering z 0..2 over two chunks.
		private final List<ZoneLayout.TrenchStrip> strips =
				List.of(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(0, 0, 31, 2), true));

		@Test
		void marksLinesWithNonBreakingBlocksInsideStripAndYRange() {
			FakeWorld world = new FakeWorld();
			for (int x = 0; x <= 15; x++) {
				world.set(x, Y, 1, RunBlock.BREAKS);
			}
			FallingRunTracker tracker = new FallingRunTracker();
			tracker.markChunk(strips, 0, 0, Y, Y, world);
			assertTrue(tracker.isLineDirty(true, 0, Y));
			assertFalse(tracker.isLineDirty(true, 1, Y), "a fully breaking line cannot change");
			assertTrue(tracker.isLineDirty(true, 2, Y));
			assertFalse(tracker.isLineDirty(true, 3, Y), "outside the strip");
			assertFalse(tracker.isLineDirty(true, 0, Y + 1), "outside the Y range");
		}

		@Test
		void onlyTheGivenChunkIsInspected() {
			// z = 1 breaks everywhere in chunk 1 but not in chunk 0: inspecting only
			// chunk 1 must not mark it.
			FakeWorld world = new FakeWorld();
			for (int x = 16; x <= 31; x++) {
				world.set(x, Y, 1, RunBlock.BREAKS);
			}
			FallingRunTracker tracker = new FallingRunTracker();
			tracker.markChunk(strips, 1, 0, Y, Y, world);
			assertFalse(tracker.isLineDirty(true, 1, Y));
			assertTrue(tracker.isLineDirty(true, 0, Y));
		}
	}

	@Test
	void lineKeysRoundTrip() {
		int[][] cases = { { -30_000_000, -64 }, { 29_999_999, 319 }, { 0, -2048 }, { -1, 2047 } };
		for (int[] c : cases) {
			for (boolean alongX : new boolean[] { true, false }) {
				long key = FallingRunTracker.lineKey(alongX, c[0], c[1]);
				assertEquals(alongX, FallingRunTracker.lineAlongX(key));
				assertEquals(c[0], FallingRunTracker.lineCross(key));
				assertEquals(c[1], FallingRunTracker.lineY(key));
			}
		}
	}
}
