package com.panyaaa256.periscan.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

/**
 * Turns the highlighted blocks of one chunk into the faces and outline lines
 * worth drawing. Pure geometry, no rendering classes, so it can be tested.
 *
 * <p>Blocks that touch inside one group are drawn as a single solid: a face
 * shared with another highlighted block is dropped, and so is every outline
 * edge that does not bend the surface (inside the solid, or flat across a
 * face). Collinear edges are joined into one long line.
 */
public final class HighlightGeometry {
	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int NORTH = 2; // -z
	public static final int SOUTH = 3; // +z
	public static final int WEST = 4; // -x
	public static final int EAST = 5; // +x

	private static final int[][] FACE_OFFSETS = {
			{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
	};

	/** Number of ints per face in {@link Mesh#faces}: block x, y, z, direction. */
	public static final int FACE_STRIDE = 4;
	/** Number of ints per line in {@link Mesh#lines}: x0, y0, z0, x1, y1, z1 (block corner coordinates). */
	public static final int LINE_STRIDE = 6;

	/**
	 * The shape of one chunk in world coordinates. A face is the side of the unit
	 * block at (x, y, z) facing the direction.
	 */
	public record Mesh(int[] faces, int[] lines) {
		public static final Mesh EMPTY = new Mesh(new int[0], new int[0]);

		public boolean isEmpty() {
			return faces.length == 0 && lines.length == 0;
		}

		public int faceCount() {
			return faces.length / FACE_STRIDE;
		}

		public int lineCount() {
			return lines.length / LINE_STRIDE;
		}
	}

	private HighlightGeometry() {
	}

	/**
	 * Builds the mesh for the blocks of one chunk.
	 *
	 * @param chunkBlocks the group's blocks (BlockPos longs) inside the chunk
	 * @param allBlocks   every block of the same group, including neighbouring chunks,
	 *                    so faces and edges at the chunk border are judged correctly
	 */
	public static Mesh build(LongSet chunkBlocks, LongSet allBlocks) {
		if (chunkBlocks == null || chunkBlocks.isEmpty()) {
			return Mesh.EMPTY;
		}
		IntArrayList faces = new IntArrayList(chunkBlocks.size() * 4);
		LongOpenHashSet[] edges = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};

		LongIterator it = chunkBlocks.iterator();
		while (it.hasNext()) {
			long pos = it.nextLong();
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			for (int dir = 0; dir < 6; dir++) {
				int[] o = FACE_OFFSETS[dir];
				if (!allBlocks.contains(BlockPos.asLong(x + o[0], y + o[1], z + o[2]))) {
					faces.add(x);
					faces.add(y);
					faces.add(z);
					faces.add(dir);
				}
			}
			collectEdges(allBlocks, edges, x, y, z);
		}

		IntArrayList lines = new IntArrayList();
		for (int axis = 0; axis < 3; axis++) {
			joinRuns(edges[axis], axis, lines);
		}
		return new Mesh(faces.toIntArray(), lines.toIntArray());
	}

	/**
	 * Adds the bending edges around the block that this block is responsible for.
	 * Each edge has four cells around it; the occupied cell with the lowest index
	 * emits it, so it is emitted once even when those cells span chunks.
	 */
	private static void collectEdges(LongSet all, LongOpenHashSet[] edges, int x, int y, int z) {
		for (int axis = 0; axis < 3; axis++) {
			for (int eu = 0; eu < 2; eu++) {
				for (int ev = 0; ev < 2; ev++) {
					// Cell index u + 2v of this block around the edge: the edge sits on the
					// block's high side (offset 1) when the block is the lower cell (u = 0).
					int ownIndex = (1 - eu) + 2 * (1 - ev);
					int lx = x;
					int ly = y;
					int lz = z;
					switch (axis) {
						case 0 -> { ly += eu; lz += ev; }
						case 1 -> { lx += eu; lz += ev; }
						default -> { lx += eu; ly += ev; }
					}
					int mask = cellMask(all, axis, lx, ly, lz);
					if ((mask & ((1 << ownIndex) - 1)) == 0 && bends(mask)) {
						edges[axis].add(BlockPos.asLong(lx, ly, lz));
					}
				}
			}
		}
	}

	/** Occupancy of the four cells around the edge starting at the lattice point, bit u + 2v. */
	private static int cellMask(LongSet all, int axis, int lx, int ly, int lz) {
		int mask = 0;
		for (int v = 0; v < 2; v++) {
			for (int u = 0; u < 2; u++) {
				int cx = lx;
				int cy = ly;
				int cz = lz;
				switch (axis) {
					case 0 -> { cy += u - 1; cz += v - 1; }
					case 1 -> { cx += u - 1; cz += v - 1; }
					default -> { cx += u - 1; cy += v - 1; }
				}
				if (all.contains(BlockPos.asLong(cx, cy, cz))) {
					mask |= 1 << (u + 2 * v);
				}
			}
		}
		return mask;
	}

	/**
	 * An edge is part of the outline when the surface turns there: one or three
	 * cells occupied (a convex or concave corner) or two diagonal cells (the
	 * solids only touch along the edge). Zero or four cells is empty or inside,
	 * and two side-by-side cells make a flat surface.
	 */
	private static boolean bends(int mask) {
		int count = Integer.bitCount(mask);
		return count == 1 || count == 3 || mask == 0b1001 || mask == 0b0110;
	}

	/** Joins edges that continue each other along the axis into one line each. */
	private static void joinRuns(LongOpenHashSet edges, int axis, IntArrayList out) {
		int stepX = axis == 0 ? 1 : 0;
		int stepY = axis == 1 ? 1 : 0;
		int stepZ = axis == 2 ? 1 : 0;
		LongIterator it = edges.iterator();
		while (it.hasNext()) {
			long pos = it.nextLong();
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			if (edges.contains(BlockPos.asLong(x - stepX, y - stepY, z - stepZ))) {
				continue; // not the start of a run
			}
			int length = 1;
			while (edges.contains(BlockPos.asLong(x + stepX * length, y + stepY * length, z + stepZ * length))) {
				length++;
			}
			out.add(x);
			out.add(y);
			out.add(z);
			out.add(x + stepX * length);
			out.add(y + stepY * length);
			out.add(z + stepZ * length);
		}
	}
}
