package com.panyaaa256.periscan.zone;

import com.panyaaa256.periscan.config.PeriScanConfig;

import java.awt.Color;
import java.util.List;

public enum Zone {
	TRENCH_OUTER("trench_outer"),
	TRENCH_INNER("trench_inner"),
	// The bottom two scanned layers of the trench body: everything except air and
	// liquids is highlighted there, and the trench inner zone excludes them.
	BOTTOM_TRENCH("bottom_trench"),
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

	public boolean enabled(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterEnabled;
			case TRENCH_INNER -> config.trenchInnerEnabled;
			case BOTTOM_TRENCH -> config.bottomTrenchEnabled;
			case EATER -> config.eaterEnabled;
		};
	}

	public List<String> blockEntries(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterBlocks;
			case TRENCH_INNER -> config.trenchInnerBlocks;
			case BOTTOM_TRENCH -> List.of(); // matches everything but liquids instead
			case EATER -> config.eaterBlocks;
		};
	}

	public boolean includeWaterlogged(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterWaterlogged;
			case TRENCH_INNER -> config.trenchInnerWaterlogged;
			case BOTTOM_TRENCH -> false;
			case EATER -> config.eaterWaterlogged;
		};
	}

	public Color color(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuterColor;
			case TRENCH_INNER -> config.trenchInnerColor;
			case BOTTOM_TRENCH -> config.bottomTrenchColor;
			case EATER -> config.eaterColor;
		};
	}
}
