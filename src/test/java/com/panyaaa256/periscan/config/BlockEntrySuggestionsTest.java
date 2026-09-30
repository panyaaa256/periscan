package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.zone.ZoneMatcher;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Needs Minecraft's registries, booted as in ZoneMatcherTest. */
class BlockEntrySuggestionsTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void blockIdsIncludeBlocksWithoutItems() {
		List<String> ids = BlockEntrySuggestions.blockIds();
		assertTrue(ids.contains("minecraft:obsidian"));
		assertTrue(ids.contains("minecraft:lava"));
		assertTrue(ids.contains("minecraft:kelp_plant"));
	}

	@Test
	void everySuggestedBlockIdIsAValidEntry() {
		assertEquals(List.of(), ZoneMatcher.invalidEntries(BlockEntrySuggestions.blockIds()));
	}

	@Test
	void pseudoTagsAreAlwaysSuggested() {
		assertEquals(ZoneMatcher.PSEUDO_TAG_ENTRIES, BlockEntrySuggestions.tagEntries(false));
		assertTrue(BlockEntrySuggestions.all(false).containsAll(ZoneMatcher.PSEUDO_TAG_ENTRIES));
	}
}
