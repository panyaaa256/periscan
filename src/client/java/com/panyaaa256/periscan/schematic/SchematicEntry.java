package com.panyaaa256.periscan.schematic;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One schematic of a schematic profile: the copied file, the corners it is
 * placed on and the height of its origin.
 */
public record SchematicEntry(String fileName, Set<Corner> corners, int originY) {
	public static final String EXTENSION = ".litematic";
	// Range the settings screen allows; values read from the file are clamped to it.
	public static final int MIN_ORIGIN_Y = -2032;
	public static final int MAX_ORIGIN_Y = 2031;
	// Just above the overworld's bedrock floor, where most peri schematics start.
	public static final int DEFAULT_ORIGIN_Y = -59;

	public SchematicEntry {
		// Copied into enum order, so entries compare equal however the set was built.
		corners = Collections.unmodifiableSet(corners.isEmpty() ? EnumSet.noneOf(Corner.class) : EnumSet.copyOf(corners));
		originY = Math.max(MIN_ORIGIN_Y, Math.min(MAX_ORIGIN_Y, originY));
	}

	/** A newly imported schematic: placed once, as saved. */
	public static SchematicEntry imported(String fileName) {
		return new SchematicEntry(fileName, EnumSet.of(Corner.MM), DEFAULT_ORIGIN_Y);
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
