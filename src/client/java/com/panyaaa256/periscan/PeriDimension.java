package com.panyaaa256.periscan;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Per-dimension constants of the dimensions peris are built in. The end has
 * no entry (peris are never built there); other dimensions (e.g. modded ones)
 * can be scanned with the world's own bounds but have no schematic placement.
 */
public enum PeriDimension {
	OVERWORLD(Level.OVERWORLD, -59, -59, "ow"),
	NETHER(Level.NETHER, 5, 5, "nether");

	private final ResourceKey<Level> key;
	private final int scanFloorY;
	private final int schematicOriginY;
	private final String defaultSetDir;

	PeriDimension(ResourceKey<Level> key, int scanFloorY, int schematicOriginY, String defaultSetDir) {
		this.key = key;
		this.scanFloorY = scanFloorY;
		this.schematicOriginY = schematicOriginY;
		this.defaultSetDir = defaultSetDir;
	}

	/** The entry for a dimension, or null if it has none. */
	public static PeriDimension of(ResourceKey<Level> key) {
		for (PeriDimension dimension : values()) {
			if (dimension.key == key) {
				return dimension;
			}
		}
		return null;
	}

	/** The entry for a dimension id ("minecraft:overworld"), or null if it has none. */
	public static PeriDimension of(String id) {
		for (PeriDimension dimension : values()) {
			if (dimension.key.identifier().toString().equals(id)) {
				return dimension;
			}
		}
		return null;
	}

	/** Lowest scanned Y: scans skip the bedrock floor below it. */
	public int scanFloorY() {
		return scanFloorY;
	}

	/** Placement Y of the schematic origin; the schematic files are saved with their origin at this height (see docs/placement.md). */
	public int schematicOriginY() {
		return schematicOriginY;
	}

	/** Schematic set folder used when /peri schematic is run without the dir argument. */
	public String defaultSetDir() {
		return defaultSetDir;
	}
}
