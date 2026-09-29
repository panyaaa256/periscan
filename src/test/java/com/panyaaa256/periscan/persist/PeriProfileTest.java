package com.panyaaa256.periscan.persist;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriProfileTest {
	@Test
	void cornersAreNormalized() {
		PeriProfile profile = PeriProfile.of("p", new ChunkPos(2, -1), new ChunkPos(-3, 4), "minecraft:overworld");
		assertEquals(new ChunkPos(-3, -1), profile.minChunk());
		assertEquals(new ChunkPos(2, 4), profile.maxChunk());
	}

	@Test
	void singleChunkIsSixteenBlocks() {
		PeriProfile profile = PeriProfile.of("p", new ChunkPos(-1, 0), new ChunkPos(-1, 0), "minecraft:overworld");
		assertEquals(-16, profile.minBlockX());
		assertEquals(-1, profile.maxBlockX());
		assertEquals(0, profile.minBlockZ());
		assertEquals(15, profile.maxBlockZ());
		assertEquals(16, profile.sizeBlocksX());
		assertEquals(16, profile.sizeBlocksZ());
	}

	@Test
	void sizeCountsWholeChunks() {
		PeriProfile profile = PeriProfile.of("p", new ChunkPos(0, 0), new ChunkPos(2, 4), "minecraft:overworld");
		assertEquals(48, profile.sizeBlocksX());
		assertEquals(80, profile.sizeBlocksZ());
	}
}
