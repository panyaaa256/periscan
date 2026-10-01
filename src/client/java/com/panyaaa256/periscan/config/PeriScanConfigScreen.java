package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.config.PeriScanConfig.BlockZoneSettings;
import com.panyaaa256.periscan.config.PeriScanConfig.ZoneSettings;
import com.panyaaa256.periscan.compat.VersionCompat;
import com.panyaaa256.periscan.scan.ScanManager;
import com.panyaaa256.periscan.zone.ZoneMatcher;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.panyaaa256.periscan.config.PeriScanConfig.MAX_FALLING_RUN;
import static com.panyaaa256.periscan.config.PeriScanConfig.MAX_SCAN_MAX_Y;
import static com.panyaaa256.periscan.config.PeriScanConfig.MAX_TRENCH_WIDTH;
import static com.panyaaa256.periscan.config.PeriScanConfig.MIN_FALLING_RUN;
import static com.panyaaa256.periscan.config.PeriScanConfig.MIN_SCAN_MAX_Y;
import static com.panyaaa256.periscan.config.PeriScanConfig.MIN_TRENCH_WIDTH;

public final class PeriScanConfigScreen {
	private static final String KEY = "periscan.config.";
	// Entries listed in the invalid-entries toast before "...".
	private static final int MAX_TOAST_ENTRIES = 5;

	private PeriScanConfigScreen() {
	}

	public static Screen create(Screen parent) {
		// Block tags are only bound client side while in a world.
		List<String> suggestions = BlockEntrySuggestions.all(Minecraft.getInstance().level != null);
		return YetAnotherConfigLib.create(PeriScanConfig.handler(), (defaults, config, builder) -> builder
				.title(Component.translatable(KEY + "title"))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable(KEY + "category.general"))
						.option(slider("ns_width", "ns_width.desc", MIN_TRENCH_WIDTH, MAX_TRENCH_WIDTH, defaults.northSouthWidth,
								() -> config.northSouthWidth, v -> config.northSouthWidth = v))
						.option(slider("ew_width", "ew_width.desc", MIN_TRENCH_WIDTH, MAX_TRENCH_WIDTH, defaults.eastWestWidth,
								() -> config.eastWestWidth, v -> config.eastWestWidth = v))
						.option(option("scan_max_y", "scan_max_y.desc", defaults.scanMaxY,
								() -> config.scanMaxY, v -> config.scanMaxY = v)
								.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(MIN_SCAN_MAX_Y, MAX_SCAN_MAX_Y))
								.build())
						.option(tickBox("show_pending", "show_pending.desc", defaults.showPendingChunks,
								() -> config.showPendingChunks, v -> config.showPendingChunks = v))
						.option(color("pending_color", defaults.pendingChunkColor,
								() -> config.pendingChunkColor, v -> config.pendingChunkColor = v))
						.option(tickBox("waterlogged_exclude_push", "waterlogged_exclude_push.desc",
								defaults.waterloggedExcludePushDestroy,
								() -> config.waterloggedExcludePushDestroy, v -> config.waterloggedExcludePushDestroy = v))
						.group(blockList("waterlogged_blacklist", "waterlogged_blacklist.desc", defaults.waterloggedBlacklist,
								suggestions,
								() -> config.waterloggedBlacklist, v -> config.waterloggedBlacklist = v))
						.build())
				// The zone settings are read through suppliers because loading the
				// config replaces the nested objects.
				.category(blockZoneCategory("trench_outer", suggestions, defaults.trenchOuter, () -> config.trenchOuter)
						.build())
				.category(blockZoneCategory("trench_inner", suggestions, defaults.trenchInner, () -> config.trenchInner)
						.option(slider("falling_run", "falling_run.desc", MIN_FALLING_RUN, MAX_FALLING_RUN,
								defaults.trenchInner.fallingRunLength,
								() -> config.trenchInner.fallingRunLength, v -> config.trenchInner.fallingRunLength = v))
						.group(blockList("zone.fence_blocks", "zone.fence_blocks.desc", defaults.trenchInner.fenceBlocks,
								suggestions,
								() -> config.trenchInner.fenceBlocks, v -> config.trenchInner.fenceBlocks = v))
						.build())
				.category(zoneCategory("bottom_trench", "bottom_trench.desc", defaults.bottomTrench, () -> config.bottomTrench)
						.build())
				.category(blockZoneCategory("eater", suggestions, defaults.eater, () -> config.eater)
						.option(tickBox("eater.include_trench", "eater.include_trench.desc", defaults.eater.includeTrench,
								() -> config.eater.includeTrench, v -> config.eater.includeTrench = v))
						.build())
				.save(() -> {
					PeriScanConfig.handler().save();
					warnInvalidEntries(config);
					// Re-derive zones and rescan with the new settings if a region is active.
					ScanManager.INSTANCE.rescan();
				}))
				.generateScreen(parent);
	}

	/** Toasts the block list entries that are neither a block id nor a tag; the config is saved regardless. */
	private static void warnInvalidEntries(PeriScanConfig config) {
		List<String> entries = new ArrayList<>(config.waterloggedBlacklist);
		entries.addAll(config.trenchOuter.blocks);
		entries.addAll(config.trenchInner.blocks);
		entries.addAll(config.trenchInner.fenceBlocks);
		entries.addAll(config.eater.blocks);
		List<String> invalid = ZoneMatcher.invalidEntries(entries).stream().map(String::trim).distinct().toList();
		if (invalid.isEmpty()) {
			return;
		}
		List<String> shown = invalid.subList(0, Math.min(invalid.size(), MAX_TOAST_ENTRIES));
		String list = String.join(", ", shown);
		if (invalid.size() > shown.size()) {
			list += ", ...";
		}
		VersionCompat.showToast(Component.translatable(KEY + "invalid_entries.title"),
				Component.translatable(KEY + "invalid_entries.message", invalid.size(), list));
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
	private static ConfigCategory.Builder blockZoneCategory(String zoneId, List<String> suggestions,
			BlockZoneSettings defaults, Supplier<? extends BlockZoneSettings> current) {
		return zoneCategory(zoneId, "zone.enabled.desc", defaults, current)
				.option(tickBox("zone.waterlogged", "zone.waterlogged.desc", defaults.waterlogged,
						() -> current.get().waterlogged, v -> current.get().waterlogged = v))
				.group(blockList("zone.blocks", "zone.blocks.desc", defaults.blocks, suggestions,
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

	/**
	 * A block id / #tag list. The setter receives a mutable copy. Entries can be
	 * picked from {@code suggestions} or typed freely (invalid ones are reported
	 * on save). 1.19.4's YACL has no dropdown controller, so it keeps plain text.
	 */
	private static ListOption<String> blockList(String nameKey, String descKey, List<String> defaultValue,
			List<String> suggestions, Supplier<List<String>> getter, Consumer<List<String>> setter) {
		return ListOption.<String>createBuilder()
				.name(Component.translatable(KEY + nameKey))
				.description(OptionDescription.of(Component.translatable(KEY + descKey)))
				.binding(defaultValue, getter, v -> setter.accept(new ArrayList<>(v)))
				//? if >=1.20 {
				.customController(opt -> new PickingDropdownController(opt, suggestions))
				//?} else
				//.controller(StringControllerBuilder::create)
				.initial("minecraft:")
				.build();
	}
}
