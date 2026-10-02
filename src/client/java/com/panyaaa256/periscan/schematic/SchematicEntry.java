package com.panyaaa256.periscan.schematic;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One schematic of a schematic profile: the copied file, the corners it is
 * placed on, the height of its origin, and whether it keeps the orientation it
 * was saved in instead of being flipped or rotated for each corner.
 */
public record SchematicEntry(String fileName, Set<Corner> corners, int originY, boolean keepOrientation) {
	public static final String EXTENSION = ".litematic";
	// Range the settings screen allows; values read from the file are clamped to it.
	public static final int MIN_ORIGIN_Y = -2032;
	public static final int MAX_ORIGIN_Y = 2031;
	// Just above the overworld's bedrock floor, where most peri schematics start.
	// Profiles can set their own default (see SchematicProfile).
	public static final int DEFAULT_ORIGIN_Y = -59;

	public SchematicEntry {
		// Copied into enum order, so entries compare equal however the set was built.
		corners = Collections.unmodifiableSet(corners.isEmpty() ? EnumSet.noneOf(Corner.class) : EnumSet.copyOf(corners));
		originY = clampOriginY(originY);
	}

	/** A newly imported schematic: placed once, as saved. */
	public static SchematicEntry imported(String fileName, int originY) {
		return new SchematicEntry(fileName, EnumSet.of(Corner.MM), originY, false);
	}

	public static int clampOriginY(int originY) {
		return Math.max(MIN_ORIGIN_Y, Math.min(MAX_ORIGIN_Y, originY));
	}

	/**
	 * Whether the name is a plain .litematic file name. Names with a path in
	 * them are rejected, as they could point outside the profile's folder.
	 */
	public static boolean isValidFileName(String fileName) {
		return fileName.length() > EXTENSION.length() && fileName.endsWith(EXTENSION)
				&& fileName.indexOf('/') < 0 && fileName.indexOf('\\') < 0;
	}

	/** The file name without its extension. */
	public String baseName() {
		return fileName.substring(0, fileName.length() - EXTENSION.length());
	}
}
