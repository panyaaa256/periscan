package com.panyaaa256.periscan.zone;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regions are given in chunks, so the smallest region is one chunk (16x16
 * blocks). Default widths as in the config: north-south 12 (along Z),
 * east-west 3 (along X).
 */
class ZoneLayoutTest {
	private static final int NS = 12;
	private static final int EW = 3;

	// In some versions (1.21.11, 26.1) loading ChunkPos touches the registries, so Minecraft must be
	// booted first (https://docs.fabricmc.net/develop/automatic-testing).
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static ZoneLayout.Settings allEnabled(int ns, int ew) {
		return new ZoneLayout.Settings(ns, ew, true, true, true, true, false);
	}

	private static ZoneLayout layout(int cx1, int cz1, int cx2, int cz2, ZoneLayout.Settings settings) {
		return ZoneLayout.of(new ChunkPos(cx1, cz1), new ChunkPos(cx2, cz2), settings);
	}

	/** 3x3 chunks at the origin: blocks 0..47 on both axes, room for all four strips. */
	private static ZoneLayout large(ZoneLayout.Settings settings) {
		return layout(0, 0, 2, 2, settings);
	}

	private static int mask(Zone... zones) {
		int mask = 0;
		for (Zone zone : zones) {
			mask |= zone.mask();
		}
		return mask;
	}

	private static boolean has(ZoneLayout layout, int x, int z, Zone zone) {
		return (layout.zoneMask(x, z) & zone.mask()) != 0;
	}

	@Nested
	class Region {
		@Test
		void cornersAreNormalizedToBlockBounds() {
			ZoneLayout layout = layout(2, 2, 0, 0, allEnabled(NS, EW));
			assertEquals(new ZoneLayout.Rect(0, 0, 47, 47), layout.region());
		}

		@Test
		void singleChunkRegionCoversSixteenBlocks() {
			ZoneLayout layout = layout(-1, 3, -1, 3, allEnabled(NS, EW));
			assertEquals(new ZoneLayout.Rect(-16, 48, -1, 63), layout.region());
		}
	}

	@Nested
	class TrenchStrips {
		@Test
		void largeRegionHasFourStrips() {
			List<ZoneLayout.TrenchStrip> strips = large(allEnabled(NS, EW)).trenchStrips();
			assertEquals(4, strips.size());
			// Strips at the Z ends run along X, strips at the X ends along Z.
			assertEquals(2, strips.stream().filter(ZoneLayout.TrenchStrip::alongX).count());
			assertTrue(strips.contains(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(0, 0, 47, 11), true)));
			assertTrue(strips.contains(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(0, 36, 47, 47), true)));
			assertTrue(strips.contains(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(0, 0, 2, 47), false)));
			assertTrue(strips.contains(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(45, 0, 47, 47), false)));
		}

		@Test
		void singleChunkTooNarrowForTwoZStripsKeepsTheOneFartherFromOrigin() {
			// 16 < 2 * 12: one Z strip. Chunk (0,0) spans z 0..15, |15| > |0| -> max side.
			ZoneLayout layout = layout(0, 0, 0, 0, allEnabled(NS, EW));
			List<ZoneLayout.TrenchStrip> zStrips = layout.trenchStrips().stream()
					.filter(ZoneLayout.TrenchStrip::alongX).toList();
			assertEquals(List.of(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(0, 4, 15, 15), true)), zStrips);
			// 16 >= 2 * 3: both X strips still fit.
			assertEquals(2, layout.trenchStrips().size() - zStrips.size());
		}

		@Test
		void singleChunkOnNegativeSideAnchorsAtMinEdge() {
			// Chunk (-1,-1) spans -16..-1: |-16| > |-1| -> min side.
			ZoneLayout layout = layout(-1, -1, -1, -1, allEnabled(NS, EW));
			List<ZoneLayout.TrenchStrip> zStrips = layout.trenchStrips().stream()
					.filter(ZoneLayout.TrenchStrip::alongX).toList();
			assertEquals(List.of(new ZoneLayout.TrenchStrip(new ZoneLayout.Rect(-16, -16, -1, -5), true)), zStrips);
		}

		@Test
		void exactlyTwiceTheWidthHoldsTwoStrips() {
			// Two chunks = 32 blocks = 2 * 16: both Z strips fit and touch in the middle.
			ZoneLayout layout = layout(0, 0, 0, 1, allEnabled(16, EW));
			assertEquals(2, layout.trenchStrips().stream().filter(ZoneLayout.TrenchStrip::alongX).count());
		}

		@Test
		void noStripsWhenTrenchInnerDisabled() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, true, false, true, true, false));
			assertTrue(layout.trenchStrips().isEmpty());
		}
	}

	@Nested
	class ZoneMask {
		@Test
		void trenchBodyIsInnerAndBottom() {
			ZoneLayout layout = large(allEnabled(NS, EW));
			assertEquals(mask(Zone.TRENCH_INNER, Zone.BOTTOM_TRENCH), layout.zoneMask(0, 0));
			assertEquals(mask(Zone.TRENCH_INNER, Zone.BOTTOM_TRENCH), layout.zoneMask(20, 11));
		}

		@Test
		void interiorIsEaterOnly() {
			assertEquals(mask(Zone.EATER), large(allEnabled(NS, EW)).zoneMask(20, 20));
		}

		@Test
		void outerLinesSitOneBlockOutsideEachStripSide() {
			ZoneLayout layout = large(allEnabled(NS, EW));
			// Z min strip (z 0..11): lines at z = -1 and z = 12.
			assertTrue(has(layout, 20, -1, Zone.TRENCH_OUTER));
			assertTrue(has(layout, 20, 12, Zone.TRENCH_OUTER));
			// Z max strip (z 36..47): lines at z = 35 and z = 48.
			assertTrue(has(layout, 20, 35, Zone.TRENCH_OUTER));
			assertTrue(has(layout, 20, 48, Zone.TRENCH_OUTER));
			// X strips (x 0..2 and 45..47): lines at x = -1, 3, 44, 48.
			assertTrue(has(layout, -1, 20, Zone.TRENCH_OUTER));
			assertTrue(has(layout, 3, 20, Zone.TRENCH_OUTER));
			assertTrue(has(layout, 44, 20, Zone.TRENCH_OUTER));
			assertTrue(has(layout, 48, 20, Zone.TRENCH_OUTER));
			// Not the neighbors of the lines.
			assertFalse(has(layout, 20, -2, Zone.TRENCH_OUTER));
			assertFalse(has(layout, 20, 13, Zone.TRENCH_OUTER));
		}

		@Test
		void outerLinesDoNotCoverTheDiagonalOutsideCorner() {
			// Lines only span the region's length, so (-1,-1) is outside every zone.
			assertEquals(0, large(allEnabled(NS, EW)).zoneMask(-1, -1));
		}

		@Test
		void eaterIncludeTrenchCoversWholeRegion() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, true, true, true, true, true));
			assertTrue(has(layout, 0, 0, Zone.EATER));
			assertTrue(has(layout, 47, 47, Zone.EATER));
			assertFalse(has(layout, -1, 20, Zone.EATER));
		}

		@Test
		void eaterExcludesTrenchesEvenWhenTrenchZonesAreDisabled() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, false, false, false, true, false));
			assertEquals(0, layout.zoneMask(0, 0));
			assertEquals(0, layout.zoneMask(20, 11));
			assertEquals(mask(Zone.EATER), layout.zoneMask(20, 12));
		}

		@Test
		void singleChunkEaterIsWhatTheStripsLeave() {
			// Chunk (0,0): X strips 0..2 / 13..15, one Z strip 4..15 -> eater x 3..12, z 0..3.
			ZoneLayout layout = layout(0, 0, 0, 0, allEnabled(NS, EW));
			assertTrue(has(layout, 3, 0, Zone.EATER));
			assertTrue(has(layout, 12, 3, Zone.EATER));
			assertFalse(has(layout, 3, 4, Zone.EATER));
			assertFalse(has(layout, 2, 0, Zone.EATER));
		}

		@Test
		void disabledZonesAreAbsent() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, false, true, false, true, false));
			for (int x = -2; x <= 49; x++) {
				for (int z = -2; z <= 49; z++) {
					assertFalse(has(layout, x, z, Zone.TRENCH_OUTER));
					assertFalse(has(layout, x, z, Zone.BOTTOM_TRENCH));
				}
			}
		}
	}

	@Nested
	class FenceLanes {
		@Test
		void onlyColumnsWithIndexModThreeEqualTwoMatter() {
			ZoneLayout layout = large(allEnabled(NS, EW));
			// Z min strip, 1-based index from the outer edge = z + 1 (x = 20 is outside the X strips).
			assertFalse(layout.fenceLaneMatters(20, 0));
			assertTrue(layout.fenceLaneMatters(20, 1));
			assertFalse(layout.fenceLaneMatters(20, 2));
			assertTrue(layout.fenceLaneMatters(20, 4));
			assertTrue(layout.fenceLaneMatters(20, 10));
			// Z max strip counts from z = 47 inward.
			assertTrue(layout.fenceLaneMatters(20, 46));
			assertFalse(layout.fenceLaneMatters(20, 45));
			assertTrue(layout.fenceLaneMatters(20, 43));
		}

		@Test
		void positionsOutsideTheStripsNeverMatter() {
			// z = 13 would be index 14 (% 3 == 2) but lies past the 12-wide strip.
			assertFalse(large(allEnabled(NS, EW)).fenceLaneMatters(20, 13));
		}

		@Test
		void widthThreeStripsNeverMatter() {
			ZoneLayout layout = large(allEnabled(NS, 3));
			assertFalse(layout.fenceLaneMatters(1, 20));
			assertFalse(layout.fenceLaneMatters(46, 20));
		}

		@Test
		void wideXStripsFollowTheSameRule() {
			ZoneLayout layout = large(allEnabled(NS, 6));
			assertTrue(layout.fenceLaneMatters(1, 20));
			assertTrue(layout.fenceLaneMatters(4, 20));
			assertFalse(layout.fenceLaneMatters(2, 20));
		}

		@Test
		void cornerQualifiesIfEitherStripDoes() {
			// (1, 0): Z strip index 1 (no), X strip index 2 (yes when width != 3).
			assertTrue(large(allEnabled(NS, 6)).fenceLaneMatters(1, 0));
			assertFalse(large(allEnabled(NS, 3)).fenceLaneMatters(1, 0));
		}

		@Test
		void droppedSingleTrenchSideNeverMatters() {
			// Chunk (0,0) only has the Z max strip (z 4..15); z = 1 would be index 2 of a min strip.
			ZoneLayout layout = layout(0, 0, 0, 0, allEnabled(NS, EW));
			assertFalse(layout.fenceLaneMatters(8, 1));
			assertTrue(layout.fenceLaneMatters(8, 14));
		}

		@Test
		void disabledTrenchInnerNeverMatters() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, true, false, true, true, false));
			assertFalse(layout.fenceLaneMatters(20, 1));
		}

		/**
		 * The rule as it was written before the strips were the single source of
		 * truth: computed from the region edges and widths directly.
		 */
		private boolean referenceMatters(ZoneLayout.Rect region, int ns, int ew, int x, int z) {
			boolean twoZ = region.maxZ() - region.minZ() + 1 >= 2 * ns;
			boolean zMax = twoZ || Math.abs(region.maxZ()) >= Math.abs(region.minZ());
			boolean zMin = twoZ || !zMax;
			boolean twoX = region.maxX() - region.minX() + 1 >= 2 * ew;
			boolean xMax = twoX || Math.abs(region.maxX()) >= Math.abs(region.minX());
			boolean xMin = twoX || !xMax;
			if (ns != 3) {
				if (zMin && z >= region.minZ() && z <= region.minZ() + ns - 1 && (z - region.minZ() + 1) % 3 == 2) {
					return true;
				}
				if (zMax && z <= region.maxZ() && z >= region.maxZ() - ns + 1 && (region.maxZ() - z + 1) % 3 == 2) {
					return true;
				}
			}
			if (ew != 3) {
				if (xMin && x >= region.minX() && x <= region.minX() + ew - 1 && (x - region.minX() + 1) % 3 == 2) {
					return true;
				}
				if (xMax && x <= region.maxX() && x >= region.maxX() - ew + 1 && (region.maxX() - x + 1) % 3 == 2) {
					return true;
				}
			}
			return false;
		}

		@Test
		void matchesTheDirectComputationInEveryColumnOfTheRegion() {
			int[][] regions = { { 0, 0, 0, 0 }, { -1, -1, -1, -1 }, { 3, -2, 3, -2 }, { 0, 0, 2, 2 },
					{ -3, 1, 0, 4 }, { -5, -5, 4, 4 }, { 1, 0, 1, 5 } };
			int[] widths = { 1, 2, 3, 4, 5, 6, 8, 12, 16, 17, 20, 33 };
			for (int[] r : regions) {
				for (int ns : widths) {
					for (int ew : widths) {
						ZoneLayout layout = layout(r[0], r[1], r[2], r[3], allEnabled(ns, ew));
						ZoneLayout.Rect region = layout.region();
						for (int x = region.minX(); x <= region.maxX(); x++) {
							for (int z = region.minZ(); z <= region.maxZ(); z++) {
								assertEquals(referenceMatters(region, ns, ew, x, z), layout.fenceLaneMatters(x, z),
										"region " + region + " ns " + ns + " ew " + ew + " at " + x + "," + z);
							}
						}
					}
				}
			}
		}
	}

	@Nested
	class Chunks {
		@Test
		void outerLinesPullInNeighborChunksButNotDiagonalCorners() {
			// Region chunks 0..2, outer lines at -1 and 48 reach chunks -1 and 3 on the
			// sides; the four diagonal corner chunks hold no zone: 5 * 5 - 4 = 21.
			ZoneLayout layout = large(allEnabled(NS, EW));
			assertEquals(21, layout.chunks().size());
			assertTrue(layout.intersectsChunk(new ChunkPos(-1, 1)));
			assertFalse(layout.intersectsChunk(new ChunkPos(-1, -1)));
		}

		@Test
		void withoutOuterLinesOnlyRegionChunks() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, false, true, true, true, false));
			assertEquals(9, layout.chunks().size());
		}

		@Test
		void allZonesDisabledMeansNothingToScan() {
			ZoneLayout layout = large(new ZoneLayout.Settings(NS, EW, false, false, false, false, false));
			assertFalse(layout.scanBounds().valid());
			assertTrue(layout.chunks().isEmpty());
			assertFalse(layout.intersectsChunk(new ChunkPos(0, 0)));
		}
	}
}
