package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.config.PeriScanConfig.BlockZoneSettings;
import com.panyaaa256.periscan.config.PeriScanConfig.ZoneSettings;
import com.panyaaa256.periscan.scan.ScanManager;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PeriScanConfigScreen {
	private static final String KEY = "periscan.config.";

	private PeriScanConfigScreen() {
	}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.create(PeriScanConfig.HANDLER, (defaults, config, builder) -> builder
				.title(Component.translatable(KEY + "title"))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable(KEY + "category.general"))
						.option(slider("ns_width", "ns_width.desc", 3, 32, defaults.northSouthWidth,
								() -> config.northSouthWidth, v -> config.northSouthWidth = v))
						.option(slider("ew_width", "ew_width.desc", 3, 32, defaults.eastWestWidth,
								() -> config.eastWestWidth, v -> config.eastWestWidth = v))
						.option(option("scan_max_y", "scan_max_y.desc", defaults.scanMaxY,
								() -> config.scanMaxY, v -> config.scanMaxY = v)
								.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(-2032, 2031))
								.build())
						.option(option("schematics_folder", "schematics_folder.desc", defaults.schematicsFolder,
								() -> config.schematicsFolder, v -> config.schematicsFolder = v)
								.controller(StringControllerBuilder::create)
								.build())
						.option(option("edge_corners", "edge_corners.desc", defaults.edgeCorners,
								() -> config.edgeCorners, v -> config.edgeCorners = v)
								.controller(opt -> EnumControllerBuilder.create(opt)
										.enumClass(PeriScanConfig.EdgeCorners.class)
										.formatValue(v -> Component.translatable(
												KEY + "edge_corners." + v.name().toLowerCase(Locale.ROOT))))
								.build())
						.option(tickBox("show_pending", "show_pending.desc", defaults.showPendingChunks,
								() -> config.showPendingChunks, v -> config.showPendingChunks = v))
						.option(color("pending_color", defaults.pendingChunkColor,
								() -> config.pendingChunkColor, v -> config.pendingChunkColor = v))
						.option(tickBox("waterlogged_exclude_push", "waterlogged_exclude_push.desc",
								defaults.waterloggedExcludePushDestroy,
								() -> config.waterloggedExcludePushDestroy, v -> config.waterloggedExcludePushDestroy = v))
						.group(blockList("waterlogged_blacklist", "waterlogged_blacklist.desc", defaults.waterloggedBlacklist,
								() -> config.waterloggedBlacklist, v -> config.waterloggedBlacklist = v))
						.build())
				// The zone settings are read through suppliers because loading the
				// config replaces the nested objects.
				.category(blockZoneCategory("trench_outer", defaults.trenchOuter, () -> config.trenchOuter)
						.build())
				.category(blockZoneCategory("trench_inner", defaults.trenchInner, () -> config.trenchInner)
						.option(slider("falling_run", "falling_run.desc", 2, 64, defaults.trenchInner.fallingRunLength,
								() -> config.trenchInner.fallingRunLength, v -> config.trenchInner.fallingRunLength = v))
						.group(blockList("zone.fence_blocks", "zone.fence_blocks.desc", defaults.trenchInner.fenceBlocks,
								() -> config.trenchInner.fenceBlocks, v -> config.trenchInner.fenceBlocks = v))
						.build())
				.category(zoneCategory("bottom_trench", "bottom_trench.desc", defaults.bottomTrench, () -> config.bottomTrench)
						.build())
				.category(blockZoneCategory("eater", defaults.eater, () -> config.eater)
						.option(tickBox("eater.include_trench", "eater.include_trench.desc", defaults.eater.includeTrench,
								() -> config.eater.includeTrench, v -> config.eater.includeTrench = v))
						.build())
				.save(() -> {
					PeriScanConfig.HANDLER.save();
					// Re-derive zones and rescan with the new settings if a region is active.
					ScanManager.INSTANCE.rescan();
				}))
				.generateScreen(parent);
	}

	/** Enabled toggle and color, shared by every zone. */
	private static ConfigCategory.Builder zoneCategory(String zoneId, String enabledDescKey,
			ZoneSettings defaults, Supplier<? extends ZoneSettings> current) {
		return ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category." + zoneId))
				.option(tickBox("zone.enabled", enabledDescKey, defaults.enabled,
						() -> current.get().enabled, v -> current.get().enabled = v))
				.option(color("zone.color", defaults.color,
						() -> current.get().color, v -> current.get().color = v));
	}

	/** A zone category plus the block list and waterlogged toggle. */
	private static ConfigCategory.Builder blockZoneCategory(String zoneId,
			BlockZoneSettings defaults, Supplier<? extends BlockZoneSettings> current) {
		return zoneCategory(zoneId, "zone.enabled.desc", defaults, current)
				.option(tickBox("zone.waterlogged", "zone.waterlogged.desc", defaults.waterlogged,
						() -> current.get().waterlogged, v -> current.get().waterlogged = v))
				.group(blockList("zone.blocks", "zone.blocks.desc", defaults.blocks,
						() -> current.get().blocks, v -> current.get().blocks = v));
	}

	/** An option builder with name, optional description (null for none) and binding. Keys are relative to {@link #KEY}. */
	private static <T> Option.Builder<T> option(String nameKey, String descKey, T defaultValue,
			Supplier<T> getter, Consumer<T> setter) {
		Option.Builder<T> builder = Option.<T>createBuilder()
				.name(Component.translatable(KEY + nameKey))
				.binding(defaultValue, getter, setter);
		if (descKey != null) {
			builder.description(OptionDescription.of(Component.translatable(KEY + descKey)));
		}
		return builder;
	}

	private static Option<Boolean> tickBox(String nameKey, String descKey, boolean defaultValue,
			Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return option(nameKey, descKey, defaultValue, getter, setter)
				.controller(TickBoxControllerBuilder::create)
				.build();
	}

	private static Option<Integer> slider(String nameKey, String descKey, int min, int max, int defaultValue,
			Supplier<Integer> getter, Consumer<Integer> setter) {
		return option(nameKey, descKey, defaultValue, getter, setter)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(1))
				.build();
	}

	private static Option<Color> color(String nameKey, Color defaultValue, Supplier<Color> getter, Consumer<Color> setter) {
		return option(nameKey, null, defaultValue, getter, setter)
				.controller(ColorControllerBuilder::create)
				.build();
	}

	/** A block id / #tag list. The setter receives a mutable copy. */
	private static ListOption<String> blockList(String nameKey, String descKey, List<String> defaultValue,
			Supplier<List<String>> getter, Consumer<List<String>> setter) {
		return ListOption.<String>createBuilder()
				.name(Component.translatable(KEY + nameKey))
				.description(OptionDescription.of(Component.translatable(KEY + descKey)))
				.binding(defaultValue, getter, v -> setter.accept(new ArrayList<>(v)))
				.controller(StringControllerBuilder::create)
				.initial("minecraft:")
				.build();
	}
}
