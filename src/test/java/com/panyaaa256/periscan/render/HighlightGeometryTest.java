package com.panyaaa256.periscan.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighlightGeometryTest {
	private static LongOpenHashSet blocks(int... xyz) {
		LongOpenHashSet set = new LongOpenHashSet();
		for (int i = 0; i < xyz.length; i += 3) {
			set.add(BlockPos.asLong(xyz[i], xyz[i + 1], xyz[i + 2]));
		}
		return set;
	}

	private static LongOpenHashSet chunkOf(LongOpenHashSet all, int chunkX, int chunkZ) {
		LongOpenHashSet in = new LongOpenHashSet();
		for (long pos : all) {
			if ((BlockPos.getX(pos) >> 4) == chunkX && (BlockPos.getZ(pos) >> 4) == chunkZ) {
				in.add(pos);
			}
		}
		return in;
	}

	private static HighlightGeometry.Mesh build(LongOpenHashSet all) {
		return HighlightGeometry.build(all, all);
	}

	/** Total length of all lines, to compare shapes regardless of how runs are joined. */
	private static int lineLength(HighlightGeometry.Mesh mesh) {
		int total = 0;
		int[] l = mesh.lines();
		for (int i = 0; i < l.length; i += HighlightGeometry.LINE_STRIDE) {
			total += Math.abs(l[i + 3] - l[i]) + Math.abs(l[i + 4] - l[i + 1]) + Math.abs(l[i + 5] - l[i + 2]);
		}
		return total;
	}

	private static Set<String> faceSet(HighlightGeometry.Mesh mesh) {
		Set<String> set = new HashSet<>();
		int[] f = mesh.faces();
		for (int i = 0; i < f.length; i += HighlightGeometry.FACE_STRIDE) {
			set.add(f[i] + "," + f[i + 1] + "," + f[i + 2] + "," + f[i + 3]);
		}
		return set;
	}

	@Test
	void emptyInputGivesEmptyMesh() {
		assertTrue(build(new LongOpenHashSet()).isEmpty());
	}

	@Test
	void singleBlockHasSixFacesAndTwelveEdges() {
		HighlightGeometry.Mesh mesh = build(blocks(3, 64, 5));
		assertEquals(6, mesh.faceCount());
		assertEquals(12, mesh.lineCount());
		assertEquals(12, lineLength(mesh));
	}

	@Test
	void twoAdjacentBlocksDropTheSharedFaceAndJoinEdges() {
		HighlightGeometry.Mesh mesh = build(blocks(0, 64, 0, 1, 64, 0));
		assertEquals(10, mesh.faceCount());
		// A 2x1x1 box: four lines of length 2 and eight of length 1; the flat edges
		// across the middle are not drawn.
		assertEquals(12, mesh.lineCount());
		assertEquals(4 * 2 + 8 * 1, lineLength(mesh));
	}

	@Test
	void twoByTwoByTwoIsOneCube() {
		LongOpenHashSet all = new LongOpenHashSet();
		for (int x = 0; x < 2; x++) {
			for (int y = 64; y < 66; y++) {
				for (int z = 0; z < 2; z++) {
					all.add(BlockPos.asLong(x, y, z));
				}
			}
		}
		HighlightGeometry.Mesh mesh = build(all);
		assertEquals(24, mesh.faceCount()); // 6 sides of 2x2 unit faces
		assertEquals(12, mesh.lineCount()); // just the cube outline
		assertEquals(12 * 2, lineLength(mesh));
	}

	@Test
	void flatFloorKeepsOnlyItsOutline() {
		LongOpenHashSet all = new LongOpenHashSet();
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				all.add(BlockPos.asLong(x, 64, z));
			}
		}
		HighlightGeometry.Mesh mesh = build(all);
		assertEquals(16 + 16 + 16, mesh.faceCount());
		assertEquals(12, mesh.lineCount());
	}

	@Test
	void blocksTouchingOnlyAtACornerAreSeparateSolids() {
		HighlightGeometry.Mesh mesh = build(blocks(0, 64, 0, 1, 65, 1));
		assertEquals(12, mesh.faceCount());
		assertEquals(24, lineLength(mesh));
	}

	@Test
	void blocksTouchingOnlyAlongAnEdgeShareThatEdge() {
		HighlightGeometry.Mesh mesh = build(blocks(0, 64, 0, 1, 65, 0));
		assertEquals(12, mesh.faceCount());
		// Two cubes, 24 unit edges, one of them shared and emitted once.
		assertEquals(23, lineLength(mesh));
	}

	@Test
	void lShapeKeepsTheConcaveCornerEdge() {
		HighlightGeometry.Mesh mesh = build(blocks(0, 64, 0, 1, 64, 0, 0, 64, 1));
		assertEquals(3 * 6 - 4, mesh.faceCount());
		// L prism: the 8-long outline on top and on the bottom, and 6 vertical corners (the
		// concave one included).
		assertEquals(2 * 8 + 6, lineLength(mesh));
	}

	@Test
	void chunkMeshesTogetherMatchTheWholeAcrossAChunkBorder() {
		LongOpenHashSet all = new LongOpenHashSet();
		for (int x = 14; x < 18; x++) {
			for (int y = 64; y < 66; y++) {
				for (int z = 14; z < 18; z++) {
					all.add(BlockPos.asLong(x, y, z));
				}
			}
		}
		HighlightGeometry.Mesh whole = build(all);
		int faces = 0;
		int length = 0;
		Set<String> faceSet = new HashSet<>();
		for (int cx = 0; cx < 2; cx++) {
			for (int cz = 0; cz < 2; cz++) {
				HighlightGeometry.Mesh part = HighlightGeometry.build(chunkOf(all, cx, cz), all);
				faces += part.faceCount();
				length += lineLength(part);
				faceSet.addAll(faceSet(part));
			}
		}
		assertEquals(whole.faceCount(), faces);
		assertEquals(faceSet(whole), faceSet);
		assertEquals(64, whole.faceCount());
		// Edges are emitted once overall; only runs are cut at the chunk border.
		assertEquals(lineLength(whole), length);
		assertEquals(16 + 16 + 4 * 2, length);
	}

	@Test
	void removingABlockAndRebuildingReflectsTheChange() {
		LongOpenHashSet all = blocks(0, 64, 0, 1, 64, 0);
		assertEquals(10, build(all).faceCount());
		all.remove(BlockPos.asLong(1, 64, 0));
		HighlightGeometry.Mesh mesh = build(all);
		assertEquals(6, mesh.faceCount());
		assertEquals(12, mesh.lineCount());
	}

	@Test
	void chunkWithNoBlocksOfItsOwnBuildsNothing() {
		LongOpenHashSet all = blocks(0, 64, 0);
		assertTrue(HighlightGeometry.build(chunkOf(all, 1, 0), all).isEmpty());
	}
}
