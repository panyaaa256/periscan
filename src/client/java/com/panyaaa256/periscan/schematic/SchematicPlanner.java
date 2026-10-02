package com.panyaaa256.periscan.schematic;

import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.persist.PeriProfile;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plans the litematica placements of a schematic profile for a peri profile
 * (see "Placing schematics" in docs/usage.md). Does not touch litematica, so
 * it can be used without it loaded.
 */
public final class SchematicPlanner {
	private SchematicPlanner() {
	}

	/** All placements of a profile share this name prefix; replacement and removal match on it. */
	public static String placementPrefix(String profileName) {
		return "peri/" + profileName + "/";
	}

	/**
	 * The placements of a schematic profile's entries (files in profileDir) for
	 * a peri profile: one per entry and chosen corner, in entry order. An entry
	 * that keeps its orientation is not transformed; it is moved so that its
	 * enclosing box still ends at the corner.
	 */
	public static List<PlannedPlacement> plan(Path profileDir, List<SchematicEntry> entries, PeriProfile profile) {
		String prefix = placementPrefix(profile.name());
		List<PlannedPlacement> plan = new ArrayList<>();
		for (SchematicEntry entry : entries) {
			for (Corner corner : entry.corners()) {
				boolean keep = entry.keepOrientation();
				plan.add(new PlannedPlacement(profileDir, entry.fileName(),
						prefix + entry.baseName() + "@" + corner.label(),
						corner.origin(profile, entry.originY()),
						keep ? Rotation.NONE : corner.rotation(), keep ? Mirror.NONE : corner.mirror(),
						keep && corner.maxX(), keep && corner.maxZ()));
			}
		}
		return plan;
	}
}
