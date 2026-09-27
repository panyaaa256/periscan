package com.panyaaa256.periscan.schematic;

import com.panyaaa256.periscan.config.PeriScanConfig.EdgeCorners;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.persist.PeriProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Plans the litematica placements of a schematic set directory for a profile
 * (see docs/placement.md): all/ files once at the min corner, edge/ files as a
 * pair on the corner pair chosen in the config (unrotated + 180deg, or X-mirror
 * + Z-mirror), and pre-sorted edge/mx, edge/mz files unmirrored + mirrored on
 * that axis. Does not touch litematica, so it can be used without it loaded.
 */
public final class SchematicPlanner {
	public static final String SCHEMATIC_EXTENSION = ".litematic";

	private SchematicPlanner() {
	}

	/**
	 * The schematic file names (sorted) of one set directory.
	 *
	 * @param edge   files directly in edge/; with pre-sorted mirror edges these
	 *               are misplaced and must be rejected by the caller
	 * @param edgeMx files in edge/mx (only read with pre-sorted mirror edges)
	 * @param edgeMz files in edge/mz (only read with pre-sorted mirror edges)
	 */
	public record SetFiles(Path setDir, List<String> all, List<String> edge, List<String> edgeMx, List<String> edgeMz) {

		public static SetFiles list(Path setDir, boolean presortedMirrorEdges) {
			Path edgeDir = setDir.resolve("edge");
			return new SetFiles(setDir,
					listSchematics(setDir.resolve("all")),
					listSchematics(edgeDir),
					presortedMirrorEdges ? listSchematics(edgeDir.resolve("mx")) : List.of(),
					presortedMirrorEdges ? listSchematics(edgeDir.resolve("mz")) : List.of());
		}

		public Path allDir() {
			return setDir.resolve("all");
		}

		public Path edgeDir() {
			return setDir.resolve("edge");
		}

		/** Number of schematic files (each may produce more than one placement). */
		public int fileCount() {
			return all.size() + edge.size() + edgeMx.size() + edgeMz.size();
		}
	}

	/** All placements of a profile share this name prefix; replacement and removal match on it. */
	public static String placementPrefix(String profileName) {
		return "peri/" + profileName + "/";
	}

	/**
	 * The placements for a set. Callers must have rejected misplaced edge/ files
	 * of pre-sorted sets; they would be planned with the edgeCorners rule here.
	 */
	public static List<PlannedPlacement> plan(SetFiles files, PeriProfile profile, int originY, EdgeCorners edgeCorners) {
		// Three of the four corner blocks of the perimeter at the origin height.
		BlockPos minCorner = new BlockPos(profile.minBlockX(), originY, profile.minBlockZ());
		BlockPos pmCorner = new BlockPos(profile.maxBlockX(), originY, profile.minBlockZ());
		BlockPos mpCorner = new BlockPos(profile.minBlockX(), originY, profile.maxBlockZ());
		BlockPos maxCorner = new BlockPos(profile.maxBlockX(), originY, profile.maxBlockZ());
		String prefix = placementPrefix(profile.name());
		Path edgeDir = files.edgeDir();

		List<PlannedPlacement> plan = new ArrayList<>();
		for (String file : files.all()) {
			plan.add(new PlannedPlacement(files.allDir(), file,
					prefix + "all/" + stripExtension(file), minCorner, Rotation.NONE, Mirror.NONE));
		}
		// Mirrored content extends away from its corner: FRONT_BACK flips X so it
		// covers -x/+z from the +x/-z corner, LEFT_RIGHT flips Z covering +x/-z
		// from the -x/+z corner.
		for (String file : files.edgeMx()) {
			String base = prefix + "edge/mx/" + stripExtension(file);
			plan.add(new PlannedPlacement(edgeDir.resolve("mx"), file, base, minCorner, Rotation.NONE, Mirror.NONE));
			plan.add(new PlannedPlacement(edgeDir.resolve("mx"), file, base + "@mx",
					pmCorner, Rotation.NONE, Mirror.FRONT_BACK));
		}
		for (String file : files.edgeMz()) {
			String base = prefix + "edge/mz/" + stripExtension(file);
			plan.add(new PlannedPlacement(edgeDir.resolve("mz"), file, base, minCorner, Rotation.NONE, Mirror.NONE));
			plan.add(new PlannedPlacement(edgeDir.resolve("mz"), file, base + "@mz",
					mpCorner, Rotation.NONE, Mirror.LEFT_RIGHT));
		}
		for (String file : files.edge()) {
			String base = prefix + "edge/" + stripExtension(file);
			if (edgeCorners == EdgeCorners.PM_MP) {
				// Mirrors, not 90/270 rotations: a rotation would swap the NS/EW
				// trench widths.
				plan.add(new PlannedPlacement(edgeDir, file, base + "@mx", pmCorner, Rotation.NONE, Mirror.FRONT_BACK));
				plan.add(new PlannedPlacement(edgeDir, file, base + "@mz", mpCorner, Rotation.NONE, Mirror.LEFT_RIGHT));
			} else {
				plan.add(new PlannedPlacement(edgeDir, file, base, minCorner, Rotation.NONE, Mirror.NONE));
				plan.add(new PlannedPlacement(edgeDir, file, base + "@180",
						maxCorner, Rotation.CLOCKWISE_180, Mirror.NONE));
			}
		}
		return plan;
	}

	/** Sorted .litematic file names directly in dir; empty if dir is missing or unreadable. */
	static List<String> listSchematics(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
					.filter(fileName -> fileName.endsWith(SCHEMATIC_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static String stripExtension(String fileName) {
		return fileName.substring(0, fileName.length() - SCHEMATIC_EXTENSION.length());
	}
}
