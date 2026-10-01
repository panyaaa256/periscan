package com.panyaaa256.periscan.schematic;

import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.persist.PeriProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicPlannerTest {
	private static final int Y = -59;

	/** One chunk (the smallest region) at chunk (0,0): blocks 0..15. */
	private static final PeriProfile ONE_CHUNK = new PeriProfile("p", 0, 0, 0, 0, "minecraft:overworld", "2026-01-01");
	/** Chunks (-2,-1)..(1,2): blocks x -32..31, z -16..47. */
	private static final PeriProfile LARGE = new PeriProfile("big", -2, -1, 1, 2, "minecraft:overworld", "2026-01-01");

	private static PlannedPlacement placement(Path dir, String file, String name, BlockPos origin,
			Rotation rotation, Mirror mirror) {
		return new PlannedPlacement(dir, file, name, origin, rotation, mirror);
	}

	@Nested
	class PlanProfile {
		private static final Path DIR = Path.of("config", "periscan", "schematics", "ow");

		private static SchematicEntry entry(String file, int originY, Corner... corners) {
			return new SchematicEntry(file, Set.of(corners), originY);
		}

		@Test
		void eachCornerHasItsOriginAndTransform() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(DIR,
					List.of(entry("wall.litematic", Y, Corner.values())), LARGE);
			assertEquals(List.of(
					placement(DIR, "wall.litematic", "peri/big/wall@--",
							new BlockPos(-32, Y, -16), Rotation.NONE, Mirror.NONE),
					placement(DIR, "wall.litematic", "peri/big/wall@+-",
							new BlockPos(31, Y, -16), Rotation.NONE, Mirror.FRONT_BACK),
					placement(DIR, "wall.litematic", "peri/big/wall@-+",
							new BlockPos(-32, Y, 47), Rotation.NONE, Mirror.LEFT_RIGHT),
					placement(DIR, "wall.litematic", "peri/big/wall@++",
							new BlockPos(31, Y, 47), Rotation.CLOCKWISE_180, Mirror.NONE)), plan);
		}

		@Test
		void entriesKeepTheirOrderAndOwnOriginY() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(DIR, List.of(
					entry("b.litematic", 5, Corner.PP, Corner.MM),
					entry("a.litematic", 70, Corner.MM)), ONE_CHUNK);
			assertEquals(List.of(
					placement(DIR, "b.litematic", "peri/p/b@--", new BlockPos(0, 5, 0), Rotation.NONE, Mirror.NONE),
					placement(DIR, "b.litematic", "peri/p/b@++", new BlockPos(15, 5, 15), Rotation.CLOCKWISE_180, Mirror.NONE),
					placement(DIR, "a.litematic", "peri/p/a@--", new BlockPos(0, 70, 0), Rotation.NONE, Mirror.NONE)), plan);
		}

		@Test
		void entriesWithoutCornersPlaceNothing() {
			assertEquals(List.of(), SchematicPlanner.plan(DIR, List.of(entry("a.litematic", Y)), LARGE));
		}
	}
}
