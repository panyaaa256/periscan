package com.panyaaa256.periscan;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Per-dimension constants of the dimensions peris are built in. The end has
 * no entry (peris are never built there); other dimensions (e.g. modded ones)
 * are scanned with the world's own bounds.
 */
public enum PeriDimension {
	OVERWORLD(Level.OVERWORLD, -59),
	NETHER(Level.NETHER, 5);

	private final ResourceKey<Level> key;
	private final int scanFloorY;

	PeriDimension(ResourceKey<Level> key, int scanFloorY) {
		this.key = key;
		this.scanFloorY = scanFloorY;
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

	/** Lowest scanned Y: scans skip the bedrock floor below it. */
	public int scanFloorY() {
		return scanFloorY;
	}
}
