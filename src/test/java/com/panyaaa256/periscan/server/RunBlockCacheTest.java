package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.scan.FallingRunTracker.RunBlock;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Chunks are stored from sections that are all missing (air), Y -64..319 as in the overworld. */
class RunBlockCacheTest {
	private static final int MIN_SECTION_Y = -4;
	private static final int SECTIONS = 24;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static RunBlockCache cacheWithChunk(int chunkX, int chunkZ) {
		RunBlockCache cache = new RunBlockCache(-59, 128);
		cache.storeChunk(chunkX, chunkZ, new LevelChunkSection[SECTIONS], MIN_SECTION_Y);
		return cache;
	}

	@Test
	void missingSectionsAreAir() {
		RunBlockCache cache = cacheWithChunk(-2, 3);
		assertEquals(RunBlock.SKIPS, cache.at(-32, -59, 48));
		assertEquals(RunBlock.SKIPS, cache.at(-17, 128, 63));
	}

	@Test
	void updatesKeepTheirOwnPosition() {
		RunBlockCache cache = cacheWithChunk(-2, 3);
		cache.update(-31, 10, 50, Blocks.SAND.defaultBlockState());
		cache.update(-30, 10, 50, Blocks.STONE.defaultBlockState());
		cache.update(-31, 11, 50, Blocks.GRAVEL.defaultBlockState());

		assertEquals(RunBlock.COUNTS, cache.at(-31, 10, 50));
		assertEquals(RunBlock.BREAKS, cache.at(-30, 10, 50));
		assertEquals(RunBlock.COUNTS, cache.at(-31, 11, 50));
		assertEquals(RunBlock.SKIPS, cache.at(-32, 10, 50), "neighbors are untouched");

		cache.update(-31, 10, 50, Blocks.AIR.defaultBlockState());
		assertEquals(RunBlock.SKIPS, cache.at(-31, 10, 50));
		assertEquals(RunBlock.BREAKS, cache.at(-30, 10, 50));
	}

	@Test
	void unknownChunksAndHeightsGiveNull() {
		RunBlockCache cache = cacheWithChunk(0, 0);
		assertNull(cache.at(16, 10, 0), "chunk not stored");
		assertNull(cache.at(0, -60, 0), "below the scanned height");
		assertNull(cache.at(0, 129, 0), "above the scanned height");

		// Updates outside the stored chunks or height change nothing.
		cache.update(16, 10, 0, Blocks.SAND.defaultBlockState());
		cache.update(0, 129, 0, Blocks.SAND.defaultBlockState());
		assertNull(cache.at(16, 10, 0));
	}
}
