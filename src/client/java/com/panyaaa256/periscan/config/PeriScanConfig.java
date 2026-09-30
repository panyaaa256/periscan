package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.zone.Zone;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PeriScanConfig {
	/** Loads and saves the config file. */
	public static ConfigClassHandler<PeriScanConfig> handler() {
		return Holder.HANDLER;
	}

	// Created on first use, so a PeriScanConfig can be made (e.g. in tests)
	// without YACL setting up its serializer.
	private static final class Holder {
		static final ConfigClassHandler<PeriScanConfig> HANDLER = ConfigClassHandler.createBuilder(PeriScanConfig.class)
				.id(PeriScanClient.id("config"))
				.serializer(config -> GsonConfigSerializerBuilder.create(config)
						.setPath(FabricLoader.getInstance().getConfigDir().resolve(PeriScanClient.MOD_ID + ".json"))
						.build())
				.build();
	}

	// Ranges the settings screen allows; sanitize() keeps values read from the file inside them too.
	public static final int MIN_TRENCH_WIDTH = 3;
	public static final int MAX_TRENCH_WIDTH = 32;
	public static final int MIN_SCAN_MAX_Y = -2032;
	public static final int MAX_SCAN_MAX_Y = 2031;
	public static final int MIN_FALLING_RUN = 2;
	public static final int MAX_FALLING_RUN = 64;

	public static PeriScanConfig get() {
		return handler().instance();
	}

	public static boolean anyZoneEnabled() {
		PeriScanConfig config = get();
		for (Zone zone : Zone.VALUES) {
			if (zone.settings(config).enabled) {
				return true;
			}
		}
		return false;
	}

	// Trench thickness in columns. northSouth runs along the Z axis, eastWest along the X axis.
	@SerialEntry
	public int northSouthWidth = 12;
	@SerialEntry
	public int eastWestWidth = 3;

	// Upper Y bound of the scan (inclusive). The lower bound is fixed per
	// dimension just above the bedrock floor (see ScanManager.scanMinY).
	@SerialEntry
	public int scanMaxY = 128;

	// Folder under litematica's schematics directory holding the peri schematic
	// sets (one subfolder per set, e.g. "ow", with "all"/"edge" inside).
	@SerialEntry
	public String schematicsFolder = "peri";

	// Which corner pair the edge/ schematics anchor to. The untransformed content
	// extends toward +x/+z from its corner. PM_MP uses mirrors, not 90/270
	// rotations, so the NS/EW trench widths stay on their axes.
	public enum EdgeCorners {
		// Unmirrored at the -x/-z corner, rotated 180deg at the +x/+z corner.
		PP_MM,
		// X-flipped (FRONT_BACK) at the +x/-z corner, Z-flipped (LEFT_RIGHT) at the -x/+z corner.
		PM_MP
	}

	@SerialEntry
	public EdgeCorners edgeCorners = EdgeCorners.PP_MM;

	// Highlight chunks inside the region that have not been scanned yet (not
	// loaded since the region was activated) as chunk-sized boxes.
	@SerialEntry
	public boolean showPendingChunks = true;
	@SerialEntry
	public Color pendingChunkColor = new Color(0xFF8800);

	/** Settings every zone has. */
	public static class ZoneSettings {
		public boolean enabled = true;
		public Color color = Color.WHITE;
	}

	/** A zone highlighting configured blocks. */
	public static class BlockZoneSettings extends ZoneSettings {
		// Entries are block ids ("minecraft:obsidian") or block tags ("#minecraft:walls").
		public List<String> blocks = new ArrayList<>();
		public boolean waterlogged = false;

		BlockZoneSettings() {
		}

		BlockZoneSettings(List<String> blocks, boolean waterlogged) {
			this.blocks = new ArrayList<>(blocks);
			this.waterlogged = waterlogged;
		}
	}

	public static final class TrenchInnerSettings extends BlockZoneSettings {
		// Blocks whose state can change under the trencher (neighbor-connecting shapes
		// and redstone-reactive blocks) only obstruct it in specific columns (1-based
		// from the perimeter edge, index % 3 == 2); entries here are only highlighted
		// there.
		public List<String> fenceBlocks = new ArrayList<>(List.of(
				"#periscan:connecting",
				"#periscan:redstone_reactive"));
		// Highlight runs of at least this many falling blocks (sand, gravel, ...) along
		// the trencher's direction of travel inside the trench body. Air, liquids and
		// blocks destroyed by a piston push don't count but don't interrupt the run
		// either; only other blocks end it.
		public int fallingRunLength = 10;

		TrenchInnerSettings() {
			// #periscan:immovable covers everything piston-immovable (chests, spawners,
			// sculk blocks, obsidian, reinforced deepslate, ...) in one entry.
			super(List.of("#periscan:immovable"), false);
		}
	}

	public static final class EaterSettings extends BlockZoneSettings {
		// true: the eater zone covers the whole specified region including the trenches;
		// false: only the interior with the trench strips removed.
		public boolean includeTrench = false;

		EaterSettings() {
			super(List.of(
					"minecraft:obsidian",
					"minecraft:ender_chest",
					"minecraft:vault",
					"minecraft:trial_spawner",
					"minecraft:reinforced_deepslate",
					"minecraft:kelp",
					"minecraft:kelp_plant"), true);
		}
	}

	@SerialEntry
	public BlockZoneSettings trenchOuter = new BlockZoneSettings(List.of(
			"minecraft:sculk_sensor",
			"minecraft:calibrated_sculk_sensor",
			"minecraft:lava"), false);

	@SerialEntry
	public TrenchInnerSettings trenchInner = new TrenchInnerSettings();

	// Bottom two scanned layers of the trench body: everything except air and
	// liquids is highlighted, and the trench inner zone excludes those layers.
	@SerialEntry
	public ZoneSettings bottomTrench = new ZoneSettings();

	@SerialEntry
	public EaterSettings eater = new EaterSettings();

	// Waterlogged-check exclusions, shared by all zones. Only waterlogged blocks are
	// highlighted (plain water sources never are). Blocks destroyed by a piston push
	// (leaves, coral, pointed dripstone, ...) can be excluded automatically.
	@SerialEntry
	public boolean waterloggedExcludePushDestroy = true;
	@SerialEntry
	public List<String> waterloggedBlacklist = new ArrayList<>();

	/**
	 * Brings values edited by hand back into what the settings screen allows:
	 * numbers are clamped to its ranges, missing values get their defaults and
	 * empty entries are dropped from the block lists. Returns whether anything
	 * changed.
	 */
	public boolean sanitize() {
		PeriScanConfig defaults = new PeriScanConfig();
		Sanitizer fix = new Sanitizer();
		northSouthWidth = fix.clamp(northSouthWidth, MIN_TRENCH_WIDTH, MAX_TRENCH_WIDTH);
		eastWestWidth = fix.clamp(eastWestWidth, MIN_TRENCH_WIDTH, MAX_TRENCH_WIDTH);
		scanMaxY = fix.clamp(scanMaxY, MIN_SCAN_MAX_Y, MAX_SCAN_MAX_Y);
		schematicsFolder = fix.orDefault(schematicsFolder, defaults.schematicsFolder);
		edgeCorners = fix.orDefault(edgeCorners, defaults.edgeCorners);
		pendingChunkColor = fix.orDefault(pendingChunkColor, defaults.pendingChunkColor);
		waterloggedBlacklist = fix.entries(waterloggedBlacklist, defaults.waterloggedBlacklist);

		trenchOuter = fix.orDefault(trenchOuter, defaults.trenchOuter);
		trenchInner = fix.orDefault(trenchInner, defaults.trenchInner);
		bottomTrench = fix.orDefault(bottomTrench, defaults.bottomTrench);
		eater = fix.orDefault(eater, defaults.eater);
		fix.zone(trenchOuter, defaults.trenchOuter);
		fix.zone(trenchInner, defaults.trenchInner);
		fix.zone(bottomTrench, defaults.bottomTrench);
		fix.zone(eater, defaults.eater);
		trenchInner.fenceBlocks = fix.entries(trenchInner.fenceBlocks, defaults.trenchInner.fenceBlocks);
		trenchInner.fallingRunLength = fix.clamp(trenchInner.fallingRunLength, MIN_FALLING_RUN, MAX_FALLING_RUN);
		return fix.changed;
	}

	/** Fixes single values for {@link #sanitize()}, remembering whether any changed. */
	private static final class Sanitizer {
		boolean changed;

		int clamp(int value, int min, int max) {
			int clamped = Math.max(min, Math.min(max, value));
			changed |= clamped != value;
			return clamped;
		}

		<T> T orDefault(T value, T defaultValue) {
			if (value == null) {
				changed = true;
				return defaultValue;
			}
			return value;
		}

		/** A missing list becomes the default; null entries are dropped. */
		List<String> entries(List<String> value, List<String> defaultValue) {
			if (value == null) {
				changed = true;
				return new ArrayList<>(defaultValue);
			}
			if (value.contains(null)) {
				changed = true;
				List<String> kept = new ArrayList<>(value);
				kept.removeIf(Objects::isNull);
				return kept;
			}
			return value;
		}

		void zone(ZoneSettings zone, ZoneSettings defaults) {
			zone.color = orDefault(zone.color, defaults.color);
			if (zone instanceof BlockZoneSettings blockZone) {
				blockZone.blocks = entries(blockZone.blocks, ((BlockZoneSettings) defaults).blocks);
			}
		}
	}
}
