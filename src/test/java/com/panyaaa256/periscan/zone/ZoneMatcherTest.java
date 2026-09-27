package com.panyaaa256.periscan.zone;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Needs Minecraft's registries (blocks and block states), booted once as the
 * Fabric docs describe: https://docs.fabricmc.net/develop/automatic-testing
 */
class ZoneMatcherTest {
	private static final ZoneMatcher.WaterloggedExclusions NO_EXCLUSIONS =
			ZoneMatcher.WaterloggedExclusions.compile(List.of(), false, new ArrayList<>());

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static ZoneMatcher matcher(String... entries) {
		List<String> invalid = new ArrayList<>();
		ZoneMatcher matcher = ZoneMatcher.compile(List.of(entries), false, NO_EXCLUSIONS, invalid);
		assertEquals(List.of(), invalid);
		return matcher;
	}

	private static boolean matches(ZoneMatcher matcher, Block block) {
		return matcher.matches(block.defaultBlockState(), 0, 0);
	}

	private static BlockState waterlogged(Block block) {
		return block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
	}

	@Nested
	class Entries {
		@Test
		void blockIdMatchesOnlyThatBlock() {
			ZoneMatcher matcher = matcher("minecraft:obsidian");
			assertTrue(matches(matcher, Blocks.OBSIDIAN));
			assertFalse(matches(matcher, Blocks.CRYING_OBSIDIAN));
		}

		@Test
		void blankEntriesAreIgnoredAndWhitespaceTrimmed() {
			ZoneMatcher matcher = matcher("", "   ", "  minecraft:obsidian ");
			assertTrue(matches(matcher, Blocks.OBSIDIAN));
		}

		@Test
		void invalidEntriesAreCollectedAsWritten() {
			List<String> invalid = new ArrayList<>();
			ZoneMatcher.compile(List.of("minecraft:not_a_block", "Bad Id!", "#bad tag!", "minecraft:obsidian"),
					false, NO_EXCLUSIONS, invalid);
			assertEquals(List.of("minecraft:not_a_block", "Bad Id!", "#bad tag!"), invalid);
		}

		@Test
		void tagEntriesAreAccepted() {
			// matcher() asserts there are no invalid entries. Matching itself needs
			// tags bound by a world's data packs, which tests do not load.
			matcher("#minecraft:walls", "#minecraft:some_unknown_tag");
		}
	}

	@Nested
	class PseudoTags {
		@Test
		void immovableFollowsVanillaPistonRules() {
			ZoneMatcher matcher = matcher("#periscan:immovable");
			// Hard-coded in PistonBaseBlock.isPushable.
			assertTrue(matches(matcher, Blocks.OBSIDIAN));
			assertTrue(matches(matcher, Blocks.CRYING_OBSIDIAN));
			assertTrue(matches(matcher, Blocks.RESPAWN_ANCHOR));
			assertTrue(matches(matcher, Blocks.REINFORCED_DEEPSLATE));
			assertTrue(matches(matcher, Blocks.END_PORTAL_FRAME), "unbreakable");
			assertTrue(matches(matcher, Blocks.CHEST), "has a block entity");
			assertTrue(matcher.matches(Blocks.PISTON.defaultBlockState()
					.setValue(BlockStateProperties.EXTENDED, true), 0, 0), "extended piston");
			assertFalse(matches(matcher, Blocks.PISTON), "retracted piston moves");
			assertFalse(matches(matcher, Blocks.BEDROCK), "terrain, not an obstruction");
			assertFalse(matches(matcher, Blocks.STONE));
			assertFalse(matches(matcher, Blocks.SAND));
		}

		@Test
		void connectingCoversFencesPanesBarsAndWalls() {
			ZoneMatcher matcher = matcher("#periscan:connecting");
			assertTrue(matches(matcher, Blocks.OAK_FENCE));
			assertTrue(matches(matcher, Blocks.GLASS_PANE));
			assertTrue(matches(matcher, Blocks.IRON_BARS));
			assertTrue(matches(matcher, Blocks.COBBLESTONE_WALL));
			assertFalse(matches(matcher, Blocks.STONE));
			assertFalse(matches(matcher, Blocks.OAK_FENCE_GATE));
		}

		@Test
		void redstoneReactiveCoversPoweredOpenExtendedTriggeredEnabled() {
			ZoneMatcher matcher = matcher("#periscan:redstone_reactive");
			assertTrue(matches(matcher, Blocks.OAK_DOOR));
			assertTrue(matches(matcher, Blocks.OAK_TRAPDOOR));
			assertTrue(matches(matcher, Blocks.PISTON));
			assertTrue(matches(matcher, Blocks.DISPENSER));
			assertTrue(matches(matcher, Blocks.HOPPER));
			assertFalse(matches(matcher, Blocks.STONE));
			// Only has "lit", which is why the default config lists it by id.
			assertFalse(matches(matcher, Blocks.REDSTONE_LAMP));
		}
	}

	@Nested
	class Lanes {
		@Test
		void laneEntriesOnlyMatchWhereThePredicateAllows() {
			List<String> invalid = new ArrayList<>();
			ZoneMatcher matcher = ZoneMatcher.compile(List.of("minecraft:obsidian"), List.of("#periscan:connecting"),
					(x, z) -> x == 1, false, NO_EXCLUSIONS, invalid);
			BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
			assertTrue(matcher.matches(fence, 1, 0));
			assertFalse(matcher.matches(fence, 2, 0));
			// Main entries match everywhere.
			assertTrue(matcher.matches(Blocks.OBSIDIAN.defaultBlockState(), 2, 0));
		}
	}

	@Nested
	class Waterlogged {
		@Test
		void onlyWaterloggedBlocksMatchWhenEnabled() {
			List<String> invalid = new ArrayList<>();
			ZoneMatcher matcher = ZoneMatcher.compile(List.of(), true, NO_EXCLUSIONS, invalid);
			assertTrue(matcher.matches(waterlogged(Blocks.OAK_STAIRS), 0, 0));
			assertFalse(matches(matcher, Blocks.OAK_STAIRS));
			assertFalse(matches(matcher, Blocks.WATER), "plain water is never highlighted");
		}

		@Test
		void disabledIgnoresWaterlogging() {
			ZoneMatcher matcher = matcher();
			assertFalse(matcher.matches(waterlogged(Blocks.OAK_STAIRS), 0, 0));
		}

		@Test
		void pushDestroyedBlocksCanBeExcluded() {
			List<String> invalid = new ArrayList<>();
			BlockState leaves = waterlogged(Blocks.OAK_LEAVES);
			ZoneMatcher excluding = ZoneMatcher.compile(List.of(), true,
					ZoneMatcher.WaterloggedExclusions.compile(List.of(), true, invalid), invalid);
			ZoneMatcher keeping = ZoneMatcher.compile(List.of(), true,
					ZoneMatcher.WaterloggedExclusions.compile(List.of(), false, invalid), invalid);
			assertFalse(excluding.matches(leaves, 0, 0));
			assertTrue(keeping.matches(leaves, 0, 0));
			// Stairs are not destroyed by pistons, so they stay.
			assertTrue(excluding.matches(waterlogged(Blocks.OAK_STAIRS), 0, 0));
		}

		@Test
		void blacklistExcludesListedBlocks() {
			List<String> invalid = new ArrayList<>();
			ZoneMatcher matcher = ZoneMatcher.compile(List.of(), true,
					ZoneMatcher.WaterloggedExclusions.compile(List.of("minecraft:oak_stairs"), false, invalid), invalid);
			assertFalse(matcher.matches(waterlogged(Blocks.OAK_STAIRS), 0, 0));
			assertTrue(matcher.matches(waterlogged(Blocks.OAK_SLAB), 0, 0));
		}

		@Test
		void listedBlocksMatchEvenIfBlacklistedForWaterlogging() {
			List<String> invalid = new ArrayList<>();
			ZoneMatcher matcher = ZoneMatcher.compile(List.of("minecraft:oak_stairs"), true,
					ZoneMatcher.WaterloggedExclusions.compile(List.of("minecraft:oak_stairs"), false, invalid), invalid);
			assertTrue(matcher.matches(waterlogged(Blocks.OAK_STAIRS), 0, 0));
		}
	}

	@Nested
	class BottomTrench {
		@Test
		void everythingButAirAndLiquids() {
			ZoneMatcher matcher = ZoneMatcher.everythingButLiquids();
			assertTrue(matches(matcher, Blocks.STONE));
			assertTrue(matches(matcher, Blocks.SAND));
			assertTrue(matcher.matches(waterlogged(Blocks.OAK_STAIRS), 0, 0));
			assertFalse(matches(matcher, Blocks.AIR));
			assertFalse(matches(matcher, Blocks.WATER));
			assertFalse(matches(matcher, Blocks.LAVA));
		}
	}
}
