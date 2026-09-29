package com.panyaaa256.periscan.zone;

import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.config.PeriScanConfig.BlockZoneSettings;
import com.panyaaa256.periscan.config.PeriScanConfig.ZoneSettings;

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

	public ZoneSettings settings(PeriScanConfig config) {
		return switch (this) {
			case TRENCH_OUTER -> config.trenchOuter;
			case TRENCH_INNER -> config.trenchInner;
			case BOTTOM_TRENCH -> config.bottomTrench;
			case EATER -> config.eater;
		};
	}

	/**
	 * Compiles this zone's matcher from the config. Unparsable config entries are
	 * appended to invalidEntries.
	 */
	public ZoneMatcher compileMatcher(PeriScanConfig config, ZoneLayout layout,
			ZoneMatcher.WaterloggedExclusions exclusions, List<String> invalidEntries) {
		if (this == BOTTOM_TRENCH) {
			return ZoneMatcher.everythingButLiquids();
		}
		BlockZoneSettings settings = (BlockZoneSettings) settings(config);
		if (this == TRENCH_INNER) {
			// Walls/fences only matter in specific trench columns (index % 3 == 2 from the edge).
			return ZoneMatcher.compile(settings.blocks, config.trenchInner.fenceBlocks, layout::fenceLaneMatters,
					settings.waterlogged, exclusions, invalidEntries);
		}
		return ZoneMatcher.compile(settings.blocks, settings.waterlogged, exclusions, invalidEntries);
	}
}
