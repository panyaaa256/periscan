package com.panyaaa256.periscan.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class PeriScanConfig {
	public static final ConfigClassHandler<PeriScanConfig> HANDLER = ConfigClassHandler.createBuilder(PeriScanConfig.class)
			.id(Identifier.fromNamespaceAndPath("periscan", "config"))
			.serializer(config -> GsonConfigSerializerBuilder.create(config)
					.setPath(FabricLoader.getInstance().getConfigDir().resolve("periscan.json"))
					.build())
			.build();

	public static PeriScanConfig get() {
		return HANDLER.instance();
	}

	@SerialEntry
	public boolean useQuarryLikeTrencher = true;

	// Trench thickness in columns. northSouth runs along the Z axis, eastWest along the X axis.
	@SerialEntry
	public int northSouthWidth = 12;
	@SerialEntry
	public int eastWestWidth = 3;

	// Upper Y bound of the scan (inclusive). The scan always starts at the world bottom.
	@SerialEntry
	public int scanMaxY = 128;

	// Entries are block ids ("minecraft:obsidian") or block tags ("#minecraft:walls").
	@SerialEntry
	public List<String> trenchOuterBlocks = new ArrayList<>(List.of(
			"minecraft:sculk_sensor",
			"minecraft:calibrated_sculk_sensor",
			"minecraft:lava"));
	@SerialEntry
	public boolean trenchOuterWaterlogged = false;
	@SerialEntry
	public Color trenchOuterColor = Color.WHITE;

	@SerialEntry
	public List<String> trenchInnerBlocks = new ArrayList<>(List.of(
			"minecraft:chest",
			"minecraft:trapped_chest",
			"minecraft:ender_chest",
			"minecraft:obsidian",
			"minecraft:crying_obsidian",
			"minecraft:vault",
			"minecraft:spawner",
			"minecraft:trial_spawner",
			"minecraft:sculk_sensor",
			"minecraft:calibrated_sculk_sensor",
			"minecraft:sculk_catalyst",
			"minecraft:sculk_shrieker",
			"minecraft:reinforced_deepslate"));
	// Walls/fences only obstruct the trencher in specific columns (1-based from the
	// perimeter edge, index % 3 == 2); entries here are only highlighted there.
	@SerialEntry
	public List<String> trenchInnerFenceBlocks = new ArrayList<>(List.of(
			"#minecraft:walls",
			"#minecraft:fences",
			"#minecraft:fence_gates"));
	@SerialEntry
	public boolean trenchInnerWaterlogged = false;
	@SerialEntry
	public Color trenchInnerColor = Color.WHITE;

	@SerialEntry
	public List<String> eaterBlocks = new ArrayList<>(List.of(
			"minecraft:obsidian",
			"minecraft:ender_chest",
			"minecraft:vault",
			"minecraft:trial_spawner",
			"minecraft:reinforced_deepslate"));
	@SerialEntry
	public boolean eaterWaterlogged = true;
	@SerialEntry
	public Color eaterColor = Color.WHITE;

	// Waterlogged-check exclusions, shared by all zones. Only waterlogged blocks are
	// highlighted (plain water sources never are). Blocks destroyed by a piston push
	// (leaves, coral, pointed dripstone, ...) can be excluded automatically.
	@SerialEntry
	public boolean waterloggedExcludePushDestroy = true;
	@SerialEntry
	public List<String> waterloggedBlacklist = new ArrayList<>();
}
