package com.panyaaa256.periscan.render;

import com.panyaaa256.periscan.scan.ChunkIndexedPositions;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;

import java.util.Collection;

/**
 * Keeps the built mesh of every chunk of one highlight group and rebuilds only
 * the chunks that changed since the last {@link #update}. Colour is not part of
 * the cache; it is applied when drawing.
 */
final class HighlightMeshes {
	private final Long2ObjectOpenHashMap<HighlightGeometry.Mesh> meshes = new Long2ObjectOpenHashMap<>();
	private final LongConsumer rebuild = this::rebuild;
	// The set the meshes were built from; a different set means they are stale.
	private ChunkIndexedPositions source;
	private boolean needsFull = true;

	/** Brings the meshes up to date with the source. */
	void update(ChunkIndexedPositions current) {
		if (current != source) {
			source = current;
			needsFull = true;
		}
		if (needsFull) {
			needsFull = false;
			meshes.clear();
			source.discardDirty();
			for (long key : source.chunkKeys().toLongArray()) {
				rebuild(key);
			}
			return;
		}
		source.drainDirty(rebuild);
	}

	private void rebuild(long chunkKey) {
		HighlightGeometry.Mesh mesh = HighlightGeometry.build(source.chunkPositions(chunkKey), source.positions());
		if (mesh.isEmpty()) {
			meshes.remove(chunkKey);
		} else {
			meshes.put(chunkKey, mesh);
		}
	}

	/** Drops all meshes (while nothing is drawn); the next update rebuilds everything. */
	void release() {
		if (!meshes.isEmpty() || !needsFull) {
			meshes.clear();
			needsFull = true;
		}
	}

	Collection<HighlightGeometry.Mesh> meshes() {
		return meshes.values();
	}
}
