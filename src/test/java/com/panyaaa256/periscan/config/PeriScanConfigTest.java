package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.scan.ScanSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriScanConfigTest {
	@Test
	void defaultsNeedNoChanges() {
		assertFalse(new PeriScanConfig().sanitize());
	}

	@Test
	void numbersAreClampedToTheScreenRanges() {
		PeriScanConfig config = new PeriScanConfig();
		config.northSouthWidth = 0;
		config.eastWestWidth = 1000;
		config.scanMaxY = Integer.MIN_VALUE;
		config.trenchInner.fallingRunLength = -5;

		assertTrue(config.sanitize());
		assertEquals(PeriScanConfig.MIN_TRENCH_WIDTH, config.northSouthWidth);
		assertEquals(PeriScanConfig.MAX_TRENCH_WIDTH, config.eastWestWidth);
		assertEquals(PeriScanConfig.MIN_SCAN_MAX_Y, config.scanMaxY);
		assertEquals(PeriScanConfig.MIN_FALLING_RUN, config.trenchInner.fallingRunLength);
	}

	@Test
	void missingValuesGetTheirDefaults() {
		PeriScanConfig defaults = new PeriScanConfig();
		PeriScanConfig config = new PeriScanConfig();
		config.pendingChunkColor = null;
		config.eater = null;
		config.trenchOuter.color = null;
		config.trenchOuter.blocks = null;

		assertTrue(config.sanitize());
		assertEquals(defaults.pendingChunkColor, config.pendingChunkColor);
		assertEquals(defaults.eater.blocks, config.eater.blocks);
		assertEquals(defaults.trenchOuter.color, config.trenchOuter.color);
		assertEquals(defaults.trenchOuter.blocks, config.trenchOuter.blocks);
	}

	@Test
	void scanSettingsCopyTheScanValues() {
		PeriScanConfig config = new PeriScanConfig();
		config.northSouthWidth = 5;
		config.bottomTrench.enabled = true;
		config.eater.includeTrench = true;
		config.trenchInner.fallingRunLength = 7;

		ScanSettings settings = config.scanSettings();
		assertEquals(5, settings.northSouthWidth());
		assertTrue(settings.bottomTrench());
		assertTrue(settings.eaterIncludeTrench());
		assertEquals(7, settings.fallingRunLength());
		assertEquals(config.eater.blocks, settings.eater().blocks());
		assertEquals(config.trenchInner.fenceBlocks, settings.fenceBlocks());

		// Later config edits do not reach settings already taken.
		config.eater.blocks.add("minecraft:stone");
		assertFalse(settings.eater().blocks().contains("minecraft:stone"));
	}

	@Test
	void nullEntriesAreDroppedFromBlockLists() {
		PeriScanConfig config = new PeriScanConfig();
		config.waterloggedBlacklist = new ArrayList<>(Arrays.asList("minecraft:oak_stairs", null));

		assertTrue(config.sanitize());
		assertEquals(List.of("minecraft:oak_stairs"), config.waterloggedBlacklist);
	}
}
