package com.panyaaa256.periscan.zone;

import com.panyaaa256.periscan.scan.ScanSettings;

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

	/**
	 * Compiles this zone's matcher from the scan settings. Unparsable config
	 * entries are appended to invalidEntries.
	 */
	public ZoneMatcher compileMatcher(ScanSettings settings, ZoneLayout layout,
			ZoneMatcher.WaterloggedExclusions exclusions, List<String> invalidEntries) {
		return switch (this) {
			case BOTTOM_TRENCH -> ZoneMatcher.everythingButLiquids();
			// Walls/fences only matter in specific trench columns (index % 3 == 2 from the edge).
			case TRENCH_INNER -> ZoneMatcher.compile(settings.trenchInner().blocks(), settings.fenceBlocks(),
					layout::fenceLaneMatters, settings.trenchInner().waterlogged(), exclusions, invalidEntries);
			case TRENCH_OUTER -> compile(settings.trenchOuter(), exclusions, invalidEntries);
			case EATER -> compile(settings.eater(), exclusions, invalidEntries);
		};
	}

	private static ZoneMatcher compile(ScanSettings.BlockZone zone,
			ZoneMatcher.WaterloggedExclusions exclusions, List<String> invalidEntries) {
		return ZoneMatcher.compile(zone.blocks(), zone.waterlogged(), exclusions, invalidEntries);
	}
}
