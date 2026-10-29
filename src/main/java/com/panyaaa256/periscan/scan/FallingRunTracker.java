package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.zone.ZoneLayout;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

/**
 * Falling-block runs in the trench body.
 *
 * <p>A run is a straight horizontal line of blocks along the trencher's
 * direction of travel (the strip's long axis). Falling blocks count toward the
 * run; air, liquids and blocks destroyed by a piston push are skipped (they
 * neither count nor break the run); only other blocks end it. Runs with at
 * least the threshold of falling blocks get all their falling blocks
 * highlighted. Runs can span chunks, so chunk scans only mark affected lines
 * dirty and whole lines are recomputed by {@link #flush}.
 */
public final class FallingRunTracker {

	/** How a block takes part in a run. */
	public enum RunBlock {
		COUNTS, SKIPS, BREAKS;

		public static RunBlock of(BlockState state) {
			if (state.getBlock() instanceof Fallable && state.getPistonPushReaction() != PushReaction.POPPED) {
				return COUNTS;
			}
			// Liquids have PushReaction.POPPED, so they are covered here too.
			if (state.isAir() || state.getPistonPushReaction() == PushReaction.POPPED) {
				return SKIPS;
			}
			return BREAKS;
		}
	}

	/** Read access to the world for the tracker. */
	@FunctionalInterface
	public interface BlockView {
		/** How the block at the position takes part in a run, or null if its chunk is not loaded. */
		RunBlock at(int x, int y, int z);
	}

	// Split by run direction so that a corner position qualifying in one
	// direction is not clobbered by a recompute of the other.
	private final ChunkIndexedPositions alongX = new ChunkIndexedPositions();
	private final ChunkIndexedPositions alongZ = new ChunkIndexedPositions();
	// Lines (direction + cross coordinate + y) whose runs need recomputing.
	private final LongOpenHashSet dirtyLines = new LongOpenHashSet();

	/** Highlighted blocks (BlockPos longs) of runs along the X axis. Do not modify. */
	public LongOpenHashSet alongX() {
		return alongX.positions();
	}

	/** Highlighted blocks (BlockPos longs) of runs along the Z axis. Do not modify. */
	public LongOpenHashSet alongZ() {
		return alongZ.positions();
	}

	/** The X-axis run highlights with their per-chunk index and change record. */
	public ChunkIndexedPositions alongXIndex() {
		return alongX;
	}

	/** The Z-axis run highlights with their per-chunk index and change record. */
	public ChunkIndexedPositions alongZIndex() {
		return alongZ;
	}

	public void clear() {
		alongX.clear();
		alongZ.clear();
		dirtyLines.clear();
	}

	/**
	 * Drops the highlights inside a chunk that is about to be rescanned. Runs
	 * span chunks, so a run may have been cut by changes made while the chunk
	 * was unloaded: the lines of the dropped highlights are marked dirty so the
	 * parts in neighboring chunks are re-evaluated on the next flush.
	 */
	public void clearChunk(int chunkX, int chunkZ) {
		clearChunk(alongX, true, chunkX, chunkZ);
		clearChunk(alongZ, false, chunkX, chunkZ);
	}

	private void clearChunk(ChunkIndexedPositions set, boolean runsAlongX, int chunkX, int chunkZ) {
		set.removeChunk(chunkX, chunkZ, key -> markLine(runsAlongX, key));
	}

	/** Marks every line of the strips inside the chunk, between minY and maxY, that the chunk might affect. */
	public void markChunk(List<ZoneLayout.TrenchStrip> strips, int chunkX, int chunkZ, int minY, int maxY,
			BlockView view) {
		int chunkMinX = chunkX << 4;
		int chunkMinZ = chunkZ << 4;
		for (ZoneLayout.TrenchStrip strip : strips) {
			ZoneLayout.Rect rect = strip.rect();
			int x0 = Math.max(rect.minX(), chunkMinX);
			int x1 = Math.min(rect.maxX(), chunkMinX + 15);
			int z0 = Math.max(rect.minZ(), chunkMinZ);
			int z1 = Math.min(rect.maxZ(), chunkMinZ + 15);
			for (int y = minY; y <= maxY; y++) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						// Skipped blocks matter too: they can bridge runs across this chunk.
						if (view.at(x, y, z) != RunBlock.BREAKS) {
							dirtyLines.add(lineKey(strip.alongX(), strip.alongX() ? z : x, y));
						}
					}
				}
			}
		}
	}

	/**
	 * Marks the lines through a changed block, if it lies in a strip and between
	 * minY and maxY. Whatever the block was or became (landed sand, a broken
	 * block, a new breaking block), its lines may have new or split runs.
	 */
	public void markPosition(List<ZoneLayout.TrenchStrip> strips, int x, int y, int z, int minY, int maxY) {
		if (y < minY || y > maxY) {
			return;
		}
		for (ZoneLayout.TrenchStrip strip : strips) {
			if (strip.rect().contains(x, z)) {
				dirtyLines.add(lineKey(strip.alongX(), strip.alongX() ? z : x, y));
			}
		}
	}

	/**
	 * Marks the lines of highlighted blocks that no longer count (a replaced
	 * block may split its run, so the whole line is recomputed on the next
	 * flush). Highlights in unloaded chunks are kept as cached.
	 */
	public void markChangedHighlights(BlockView view) {
		markChangedHighlights(alongX, true, view);
		markChangedHighlights(alongZ, false, view);
	}

	private void markChangedHighlights(ChunkIndexedPositions set, boolean runsAlongX, BlockView view) {
		LongIterator it = set.positions().iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			RunBlock block = view.at(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
			if (block != null && block != RunBlock.COUNTS) {
				markLine(runsAlongX, key);
			}
		}
	}

	private void markLine(boolean runsAlongX, long blockKey) {
		dirtyLines.add(lineKey(runsAlongX,
				runsAlongX ? BlockPos.getZ(blockKey) : BlockPos.getX(blockKey), BlockPos.getY(blockKey)));
	}

	/**
	 * Recomputes every dirty line across the whole region (trench strips span the
	 * full region length on their axis). Thresholds below 1 are treated as 1.
	 */
	public void flush(BlockView view, ZoneLayout.Rect region, int threshold) {
		int minRun = Math.max(1, threshold);
		LongIterator it = dirtyLines.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			recomputeLine(view, region, minRun, lineAlongX(key), lineCross(key), lineY(key));
		}
		dirtyLines.clear();
	}

	/**
	 * Loaded parts of the line are brought up to date; highlights in unloaded
	 * chunks are left as cached, and an unloaded stretch ends the run.
	 */
	private void recomputeLine(BlockView view, ZoneLayout.Rect region, int threshold,
			boolean runsAlongX, int cross, int y) {
		ChunkIndexedPositions target = runsAlongX ? alongX : alongZ;
		int from = runsAlongX ? region.minX() : region.minZ();
		int to = runsAlongX ? region.maxX() : region.maxZ();
		LongArrayList run = new LongArrayList();
		for (int pos = from; pos <= to; pos++) {
			int x = runsAlongX ? pos : cross;
			int z = runsAlongX ? cross : pos;
			RunBlock block = view.at(x, y, z);
			if (block == null) {
				endRun(target, run, threshold);
				continue;
			}
			long key = BlockPos.asLong(x, y, z);
			if (block == RunBlock.COUNTS) {
				run.add(key);
			} else {
				target.remove(key);
				if (block == RunBlock.BREAKS) {
					endRun(target, run, threshold);
				}
			}
		}
		endRun(target, run, threshold);
	}

	private static void endRun(ChunkIndexedPositions target, LongArrayList run, int threshold) {
		boolean qualifies = run.size() >= threshold;
		for (int i = 0; i < run.size(); i++) {
			if (qualifies) {
				target.add(run.getLong(i));
			} else {
				target.remove(run.getLong(i));
			}
		}
		run.clear();
	}

	/** Whether the line is waiting for the next flush. For tests. */
	boolean isLineDirty(boolean runsAlongX, int cross, int y) {
		return dirtyLines.contains(lineKey(runsAlongX, cross, y));
	}

	// Line keys: cross coordinate (high 32 bits) | y + 2048 (12 bits) | direction flag (1 bit).

	static long lineKey(boolean runsAlongX, int cross, int y) {
		return ((long) cross << 32) | ((long) (y + 2048) << 1) | (runsAlongX ? 1L : 0L);
	}

	static boolean lineAlongX(long key) {
		return (key & 1) != 0;
	}

	static int lineCross(long key) {
		return (int) (key >> 32);
	}

	static int lineY(long key) {
		return (int) ((key >>> 1) & 0xFFF) - 2048;
	}
}
