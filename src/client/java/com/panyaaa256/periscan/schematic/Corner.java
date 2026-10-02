package com.panyaaa256.periscan.schematic;

import com.panyaaa256.periscan.persist.PeriProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A corner of the perimeter a schematic can be placed on, named by the signs
 * of its X and Z side ("+-" is the +x/-z corner). Schematics are saved for the
 * -x/-z corner, extending toward +x/+z; the other corners transform them so
 * the content extends into the perimeter from there too. A schematic that
 * keeps its orientation is moved into the corner instead (see SchematicPlanner).
 */
public enum Corner {
	MM("--", false, false, Rotation.NONE, Mirror.NONE),
	// Mirrors, not 90/270 rotations: a rotation would swap the NS/EW trench widths.
	// FRONT_BACK flips X, LEFT_RIGHT flips Z.
	PM("+-", true, false, Rotation.NONE, Mirror.FRONT_BACK),
	MP("-+", false, true, Rotation.NONE, Mirror.LEFT_RIGHT),
	PP("++", true, true, Rotation.CLOCKWISE_180, Mirror.NONE);

	private final String label;
	private final boolean maxX;
	private final boolean maxZ;
	private final Rotation rotation;
	private final Mirror mirror;

	Corner(String label, boolean maxX, boolean maxZ, Rotation rotation, Mirror mirror) {
		this.label = label;
		this.maxX = maxX;
		this.maxZ = maxZ;
		this.rotation = rotation;
		this.mirror = mirror;
	}

	/** The corner with the given label, or null if there is none. */
	public static Corner byLabel(String label) {
		for (Corner corner : values()) {
			if (corner.label.equals(label)) {
				return corner;
			}
		}
		return null;
	}

	/** The X and Z signs, e.g. "+-"; used in files, placement names and the settings screen. */
	public String label() {
		return label;
	}

	/** Whether this corner is on the +x side of the perimeter. */
	public boolean maxX() {
		return maxX;
	}

	/** Whether this corner is on the +z side of the perimeter. */
	public boolean maxZ() {
		return maxZ;
	}

	public Rotation rotation() {
		return rotation;
	}

	public Mirror mirror() {
		return mirror;
	}

	/** This corner's block of the perimeter at the given height. */
	public BlockPos origin(PeriProfile profile, int y) {
		return new BlockPos(maxX ? profile.maxBlockX() : profile.minBlockX(), y,
				maxZ ? profile.maxBlockZ() : profile.minBlockZ());
	}
}
