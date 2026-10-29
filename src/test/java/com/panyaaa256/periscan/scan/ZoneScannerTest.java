package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.scan.ScanSettings.BlockZone;
import com.panyaaa256.periscan.zone.Zone;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The region is 3x3 chunks at the origin (blocks 0..47) with the default trench
 * widths, so (24, 5) is in the north trench body and (24, 24) in the eater zone.
 */
class ZoneScannerTest {
	private static final int MIN_Y = -59;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static ZoneScanner scanner(boolean bottomTrench) {
		ScanSettings settings = new ScanSettings(12, 3, 128,
				new BlockZone(true, List.of("minecraft:lava"), false),
				new BlockZone(true, List.of("#periscan:immovable"), false),
				bottomTrench,
				new BlockZone(true, List.of("minecraft:obsidian"), false),
				List.of(), 10, false, true, List.of());
		List<String> invalid = new ArrayList<>();
		ZoneScanner scanner = new ZoneScanner(new ChunkPos(0, 0), new ChunkPos(2, 2), settings, invalid);
		assertEquals(List.of(), invalid);
		return scanner;
	}

	/** The zones that highlight the block, with the position each reported. */
	private static Map<Zone, Long> zonesOf(ZoneScanner scanner, int x, int y, int z, BlockState state) {
		Map<Zone, Long> zones = new EnumMap<>(Zone.class);
		scanner.scanBlock(x, y, z, state, MIN_Y, zones::put);
		return zones;
	}

	@Test
	void eaterZoneHighlightsItsBlocks() {
		assertEquals(Map.of(Zone.EATER, BlockPos.asLong(24, 0, 24)),
				zonesOf(scanner(false), 24, 0, 24, Blocks.OBSIDIAN.defaultBlockState()));
		assertEquals(Map.of(), zonesOf(scanner(false), 24, 0, 24, Blocks.STONE.defaultBlockState()));
	}

	@Test
	void bottomLayersBelongToTheBottomTrenchWhenItIsEnabled() {
		ZoneScanner scanner = scanner(true);
		assertEquals(2, scanner.bottomTrenchLayers());
		// Everything but air and liquids in the two lowest layers, and nothing for the trench inner zone there.
		assertEquals(Map.of(Zone.BOTTOM_TRENCH, BlockPos.asLong(24, MIN_Y, 5)),
				zonesOf(scanner, 24, MIN_Y, 5, Blocks.OBSIDIAN.defaultBlockState()));
		assertEquals(Map.of(Zone.BOTTOM_TRENCH, BlockPos.asLong(24, MIN_Y + 1, 5)),
				zonesOf(scanner, 24, MIN_Y + 1, 5, Blocks.STONE.defaultBlockState()));
		// Above them the trench inner zone takes over.
		assertEquals(Map.of(Zone.TRENCH_INNER, BlockPos.asLong(24, MIN_Y + 2, 5)),
				zonesOf(scanner, 24, MIN_Y + 2, 5, Blocks.OBSIDIAN.defaultBlockState()));
	}

	@Test
	void trenchInnerTakesEveryLayerWhileTheBottomTrenchIsDisabled() {
		ZoneScanner scanner = scanner(false);
		assertEquals(0, scanner.bottomTrenchLayers());
		assertEquals(Map.of(Zone.TRENCH_INNER, BlockPos.asLong(24, MIN_Y, 5)),
				zonesOf(scanner, 24, MIN_Y, 5, Blocks.OBSIDIAN.defaultBlockState()));
	}

	@Test
	void airIsNeverHighlighted() {
		assertTrue(zonesOf(scanner(true), 24, MIN_Y, 5, Blocks.AIR.defaultBlockState()).isEmpty());
	}
}
