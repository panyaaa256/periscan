package com.panyaaa256.periscan.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.util.Optional;

/**
 * A chunk the server has not loaded, as read from the save.
 *
 * @param key      the chunk (ChunkPos long)
 * @param sections the chunk's sections, lowest first, starting at the level's
 *                 lowest section (null entries are air); null unless the chunk is GENERATED
 */
record SavedChunk(long key, State state, LevelChunkSection[] sections) {

	enum State {
		GENERATED,
		// No data, or a chunk whose generation has not finished (not FULL).
		UNGENERATED,
		// The data could not be read or parsed (see the server log).
		UNREADABLE
	}

	static SavedChunk failed(long key) {
		return new SavedChunk(key, State.UNREADABLE, null);
	}

	/**
	 * Parses chunk data as ChunkMap.readChunk returns it (already upgraded to
	 * this version). Like vanilla's own parsing, this runs off the server thread:
	 * it does not touch the world, only the level's height and palettes.
	 */
	static SavedChunk parse(ServerLevel level, long key, Optional<CompoundTag> tag) {
		if (tag.isEmpty() || SerializableChunkData.getChunkStatusFromTag(tag.get()) != ChunkStatus.FULL) {
			return new SavedChunk(key, State.UNGENERATED, null);
		}
		SerializableChunkData data = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag.get());
		if (data == null) {
			return failed(key);
		}
		LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
		for (SerializableChunkData.SectionData section : data.sectionData()) {
			// Sections outside the level's height (light only) have no blocks.
			if (section.chunkSection() != null) {
				sections[section.y() - level.getMinSectionY()] = section.chunkSection();
			}
		}
		return new SavedChunk(key, State.GENERATED, sections);
	}
}
