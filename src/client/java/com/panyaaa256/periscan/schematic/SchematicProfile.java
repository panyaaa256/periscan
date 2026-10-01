package com.panyaaa256.periscan.schematic;

import java.util.List;

/**
 * A schematic profile as saved: its schematics in placement order, and the
 * origin height newly imported schematics start with.
 */
public record SchematicProfile(int defaultOriginY, List<SchematicEntry> entries) {
	/** What a profile that does not exist yet starts as. */
	public static final SchematicProfile EMPTY = new SchematicProfile(SchematicEntry.DEFAULT_ORIGIN_Y, List.of());

	public SchematicProfile {
		defaultOriginY = SchematicEntry.clampOriginY(defaultOriginY);
		entries = List.copyOf(entries);
	}
}
