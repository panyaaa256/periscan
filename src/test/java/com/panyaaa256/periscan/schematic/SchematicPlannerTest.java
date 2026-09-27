package com.panyaaa256.periscan.schematic;

import com.panyaaa256.periscan.config.PeriScanConfig.EdgeCorners;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.persist.PeriProfile;
import com.panyaaa256.periscan.schematic.SchematicPlanner.SetFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicPlannerTest {
	private static final Path SET = Path.of("schematics", "peri", "ow");
	private static final int Y = -59;

	/** One chunk (the smallest region) at chunk (0,0): blocks 0..15. */
	private static final PeriProfile ONE_CHUNK = new PeriProfile("p", 0, 0, 0, 0, "minecraft:overworld", "2026-01-01");
	/** Chunks (-2,-1)..(1,2): blocks x -32..31, z -16..47. */
	private static final PeriProfile LARGE = new PeriProfile("big", -2, -1, 1, 2, "minecraft:overworld", "2026-01-01");

	private static SetFiles files(List<String> all, List<String> edge, List<String> mx, List<String> mz) {
		return new SetFiles(SET, all, edge, mx, mz);
	}

	private static PlannedPlacement placement(Path dir, String file, String name, BlockPos origin,
			Rotation rotation, Mirror mirror) {
		return new PlannedPlacement(dir, file, name, origin, rotation, mirror);
	}

	@Test
	void placementPrefixIsPerProfile() {
		assertEquals("peri/p/", SchematicPlanner.placementPrefix("p"));
	}

	@Nested
	class Plan {
		@Test
		void allFilesOnceAtMinCorner() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of("base.litematic"), List.of(), List.of(), List.of()), ONE_CHUNK, Y, EdgeCorners.PP_MM);
			assertEquals(List.of(placement(SET.resolve("all"), "base.litematic", "peri/p/all/base",
					new BlockPos(0, Y, 0), Rotation.NONE, Mirror.NONE)), plan);
		}

		@Test
		void edgePpMmIsUnrotatedAtMinAndRotated180AtMax() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of(), List.of("wall.litematic"), List.of(), List.of()), LARGE, Y, EdgeCorners.PP_MM);
			Path edge = SET.resolve("edge");
			assertEquals(List.of(
					placement(edge, "wall.litematic", "peri/big/edge/wall",
							new BlockPos(-32, Y, -16), Rotation.NONE, Mirror.NONE),
					placement(edge, "wall.litematic", "peri/big/edge/wall@180",
							new BlockPos(31, Y, 47), Rotation.CLOCKWISE_180, Mirror.NONE)), plan);
		}

		@Test
		void edgePmMpIsMirroredOnTheOtherCornerPair() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of(), List.of("wall.litematic"), List.of(), List.of()), LARGE, Y, EdgeCorners.PM_MP);
			Path edge = SET.resolve("edge");
			assertEquals(List.of(
					placement(edge, "wall.litematic", "peri/big/edge/wall@mx",
							new BlockPos(31, Y, -16), Rotation.NONE, Mirror.FRONT_BACK),
					placement(edge, "wall.litematic", "peri/big/edge/wall@mz",
							new BlockPos(-32, Y, 47), Rotation.NONE, Mirror.LEFT_RIGHT)), plan);
		}

		@Test
		void presortedMxAndMzAreMirroredOnTheirAxisOnly() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of(), List.of(), List.of("a.litematic"), List.of("b.litematic")), LARGE, 5, EdgeCorners.PP_MM);
			Path edge = SET.resolve("edge");
			assertEquals(List.of(
					placement(edge.resolve("mx"), "a.litematic", "peri/big/edge/mx/a",
							new BlockPos(-32, 5, -16), Rotation.NONE, Mirror.NONE),
					placement(edge.resolve("mx"), "a.litematic", "peri/big/edge/mx/a@mx",
							new BlockPos(31, 5, -16), Rotation.NONE, Mirror.FRONT_BACK),
					placement(edge.resolve("mz"), "b.litematic", "peri/big/edge/mz/b",
							new BlockPos(-32, 5, -16), Rotation.NONE, Mirror.NONE),
					placement(edge.resolve("mz"), "b.litematic", "peri/big/edge/mz/b@mz",
							new BlockPos(-32, 5, 47), Rotation.NONE, Mirror.LEFT_RIGHT)), plan);
		}

		@Test
		void presortedEdgesIgnoreTheEdgeCornersSetting() {
			SetFiles files = files(List.of(), List.of(), List.of("a.litematic"), List.of("b.litematic"));
			assertEquals(SchematicPlanner.plan(files, LARGE, Y, EdgeCorners.PP_MM),
					SchematicPlanner.plan(files, LARGE, Y, EdgeCorners.PM_MP));
		}

		@Test
		void oneChunkCornersAreSixteenBlocksApart() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of(), List.of("wall.litematic"), List.of(), List.of()), ONE_CHUNK, Y, EdgeCorners.PP_MM);
			assertEquals(new BlockPos(0, Y, 0), plan.get(0).origin());
			assertEquals(new BlockPos(15, Y, 15), plan.get(1).origin());
		}

		@Test
		void orderIsAllThenMxThenMzThenEdge() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of("1.litematic"), List.of("4.litematic"), List.of("2.litematic"), List.of("3.litematic")),
					ONE_CHUNK, Y, EdgeCorners.PP_MM);
			assertEquals(List.of("1.litematic", "2.litematic", "2.litematic", "3.litematic", "3.litematic",
					"4.litematic", "4.litematic"), plan.stream().map(PlannedPlacement::fileName).toList());
		}

		@Test
		void everyNameStartsWithTheProfilePrefix() {
			List<PlannedPlacement> plan = SchematicPlanner.plan(
					files(List.of("a.litematic"), List.of("b.litematic"), List.of("c.litematic"), List.of("d.litematic")),
					LARGE, Y, EdgeCorners.PM_MP);
			assertTrue(plan.stream().allMatch(p -> p.placementName().startsWith("peri/big/")));
		}
	}

	@Nested
	class ListFiles {
		@TempDir
		Path setDir;

		private void touch(String relative) throws IOException {
			Path file = setDir.resolve(relative);
			Files.createDirectories(file.getParent());
			Files.createFile(file);
		}

		@Test
		void onlyLitematicFilesSorted() throws IOException {
			touch("all/b.litematic");
			touch("all/a.litematic");
			touch("all/notes.txt");
			Files.createDirectories(setDir.resolve("all/dir.litematic"));
			SetFiles files = SetFiles.list(setDir, false);
			assertEquals(List.of("a.litematic", "b.litematic"), files.all());
		}

		@Test
		void missingFoldersAreEmpty() {
			SetFiles files = SetFiles.list(setDir, true);
			assertEquals(0, files.fileCount());
		}

		@Test
		void mxAndMzAreIgnoredUnlessPresorted() throws IOException {
			touch("edge/wall.litematic");
			touch("edge/mx/a.litematic");
			touch("edge/mz/b.litematic");
			SetFiles files = SetFiles.list(setDir, false);
			assertEquals(List.of("wall.litematic"), files.edge());
			assertEquals(List.of(), files.edgeMx());
			assertEquals(List.of(), files.edgeMz());
			assertEquals(1, files.fileCount());
		}

		@Test
		void presortedReadsMxMzAndStillReportsMisplacedEdgeFiles() throws IOException {
			touch("edge/wall.litematic");
			touch("edge/mx/a.litematic");
			touch("edge/mz/b.litematic");
			SetFiles files = SetFiles.list(setDir, true);
			assertEquals(List.of("wall.litematic"), files.edge());
			assertEquals(List.of("a.litematic"), files.edgeMx());
			assertEquals(List.of("b.litematic"), files.edgeMz());
		}
	}
}
