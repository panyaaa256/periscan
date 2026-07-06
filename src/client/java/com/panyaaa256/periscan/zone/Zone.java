package com.panyaaa256.periscan.zone;

import com.panyaaa256.periscan.config.PeriScanConfig;

import java.awt.Color;
import java.util.List;

public enum Zone {
	TRENCH_OUTER("trench_outer"),
	TRENCH_INNER("trench_inner"),
	EATER("eater");

	public static final Zone[] VALUES = values();

	private final String id;

	Zone(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public int mask() {
		return 1 << ordinal();
	}

	public List<String> blockEntries(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterBlocks;
			case TRENCH_INNER -> config.trenchInnerBlocks;
			case EATER -> config.eaterBlocks;
		};
	}

	public boolean includeWaterlogged(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterWaterlogged;
			case TRENCH_INNER -> config.trenchInnerWaterlogged;
			case EATER -> config.eaterWaterlogged;
		};
	}

	public Color color(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterColor;
			case TRENCH_INNER -> config.trenchInnerColor;
			case EATER -> config.eaterColor;
		};
	}
}
