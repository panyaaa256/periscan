package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.scan.ScanManager;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PeriScanConfigScreen {
	private PeriScanConfigScreen() {
	}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.create(PeriScanConfig.HANDLER, (defaults, config, builder) -> builder
				.title(Component.translatable("periscan.config.title"))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("periscan.config.category.general"))
						.option(Option.<Boolean>createBuilder()
								.name(Component.translatable("periscan.config.quarry_like"))
								.description(OptionDescription.of(Component.translatable("periscan.config.quarry_like.desc")))
								.binding(defaults.useQuarryLikeTrencher,
										() -> config.useQuarryLikeTrencher,
										v -> config.useQuarryLikeTrencher = v)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Integer>createBuilder()
								.name(Component.translatable("periscan.config.ns_width"))
								.description(OptionDescription.of(Component.translatable("periscan.config.ns_width.desc")))
								.binding(defaults.northSouthWidth,
										() -> config.northSouthWidth,
										v -> config.northSouthWidth = v)
								.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(3, 32).step(1))
								.build())
						.option(Option.<Integer>createBuilder()
								.name(Component.translatable("periscan.config.ew_width"))
								.description(OptionDescription.of(Component.translatable("periscan.config.ew_width.desc")))
								.binding(defaults.eastWestWidth,
										() -> config.eastWestWidth,
										v -> config.eastWestWidth = v)
								.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(3, 32).step(1))
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Component.translatable("periscan.config.waterlogged_exclude_push"))
								.description(OptionDescription.of(Component.translatable("periscan.config.waterlogged_exclude_push.desc")))
								.binding(defaults.waterloggedExcludePushDestroy,
										() -> config.waterloggedExcludePushDestroy,
										v -> config.waterloggedExcludePushDestroy = v)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.group(ListOption.<String>createBuilder()
								.name(Component.translatable("periscan.config.waterlogged_blacklist"))
								.description(OptionDescription.of(Component.translatable("periscan.config.waterlogged_blacklist.desc")))
								.binding(defaults.waterloggedBlacklist,
										() -> config.waterloggedBlacklist,
										v -> config.waterloggedBlacklist = new ArrayList<>(v))
								.controller(StringControllerBuilder::create)
								.initial("minecraft:")
								.build())
						.build())
				.category(zoneCategory("periscan.config.category.trench_outer",
						defaults.trenchOuterBlocks, () -> config.trenchOuterBlocks, v -> config.trenchOuterBlocks = new ArrayList<>(v),
						defaults.trenchOuterWaterlogged, () -> config.trenchOuterWaterlogged, v -> config.trenchOuterWaterlogged = v,
						defaults.trenchOuterColor, () -> config.trenchOuterColor, v -> config.trenchOuterColor = v))
				.category(zoneCategory("periscan.config.category.trench_inner",
						defaults.trenchInnerBlocks, () -> config.trenchInnerBlocks, v -> config.trenchInnerBlocks = new ArrayList<>(v),
						defaults.trenchInnerWaterlogged, () -> config.trenchInnerWaterlogged, v -> config.trenchInnerWaterlogged = v,
						defaults.trenchInnerColor, () -> config.trenchInnerColor, v -> config.trenchInnerColor = v))
				.category(zoneCategory("periscan.config.category.eater",
						defaults.eaterBlocks, () -> config.eaterBlocks, v -> config.eaterBlocks = new ArrayList<>(v),
						defaults.eaterWaterlogged, () -> config.eaterWaterlogged, v -> config.eaterWaterlogged = v,
						defaults.eaterColor, () -> config.eaterColor, v -> config.eaterColor = v))
				.save(() -> {
					PeriScanConfig.HANDLER.save();
					// Re-derive zones and rescan with the new settings if a region is active.
					ScanManager.INSTANCE.rescan();
				}))
				.generateScreen(parent);
	}

	private static ConfigCategory zoneCategory(String nameKey,
			List<String> defaultBlocks, Supplier<List<String>> getBlocks, Consumer<List<String>> setBlocks,
			boolean defaultWaterlogged, Supplier<Boolean> getWaterlogged, Consumer<Boolean> setWaterlogged,
			Color defaultColor, Supplier<Color> getColor, Consumer<Color> setColor) {
		return ConfigCategory.createBuilder()
				.name(Component.translatable(nameKey))
				.option(Option.<Color>createBuilder()
						.name(Component.translatable("periscan.config.zone.color"))
						.binding(defaultColor, getColor, setColor)
						.controller(ColorControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("periscan.config.zone.waterlogged"))
						.description(OptionDescription.of(Component.translatable("periscan.config.zone.waterlogged.desc")))
						.binding(defaultWaterlogged, getWaterlogged, setWaterlogged)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.group(ListOption.<String>createBuilder()
						.name(Component.translatable("periscan.config.zone.blocks"))
						.description(OptionDescription.of(Component.translatable("periscan.config.zone.blocks.desc")))
						.binding(defaultBlocks, getBlocks, setBlocks)
						.controller(StringControllerBuilder::create)
						.initial("minecraft:")
						.build())
				.build();
	}
}
