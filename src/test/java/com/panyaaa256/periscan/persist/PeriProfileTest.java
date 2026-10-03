package com.panyaaa256.periscan.persist;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriProfileTest {
	// In some versions (1.21.11, 26.1) loading ChunkPos touches the registries, so Minecraft must be
	// booted first (https://docs.fabricmc.net/develop/automatic-testing).
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

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

	@Test
	void sidesUpToTheLimitAreAllowed() {
		int last = PeriProfile.MAX_SIDE_CHUNKS - 1;
		PeriProfile profile = PeriProfile.of("p", new ChunkPos(0, 0), new ChunkPos(last, last), "minecraft:overworld");
		assertEquals(PeriProfile.MAX_SIDE_CHUNKS, profile.sideChunksX());
		assertFalse(profile.exceedsMaxSize());
	}

	@Test
	void aSideOverTheLimitIsTooLarge() {
		PeriProfile profile = PeriProfile.of("p", new ChunkPos(0, 0),
				new ChunkPos(0, PeriProfile.MAX_SIDE_CHUNKS), "minecraft:overworld");
		assertTrue(profile.exceedsMaxSize());
	}

	@Test
	void blockCoordinatesAtTheWorldEdgeFitInAnInt() {
		int edge = PeriProfile.MAX_CHUNK_COORD;
		PeriProfile profile = PeriProfile.of("p", new ChunkPos(-edge, -edge), new ChunkPos(edge, edge), "minecraft:overworld");
		assertEquals(-edge * 16L, profile.minBlockX());
		assertEquals(edge * 16L + 15, profile.maxBlockZ());
	}

	@Test
	void extremeCoordinatesFromDiskDoNotOverflow() {
		PeriProfile profile = new PeriProfile("p", Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0,
				"minecraft:overworld", null);
		assertEquals(1L << 32, profile.sideChunksX());
		assertTrue(profile.exceedsMaxSize());
	}
}
