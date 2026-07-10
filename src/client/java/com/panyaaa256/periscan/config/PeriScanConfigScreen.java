package com.panyaaa256.periscan.config;

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
	private PeriScanConfigScreen() {
	}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.create(PeriScanConfig.HANDLER, (defaults, config, builder) -> builder
				.title(Component.translatable("periscan.config.title"))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("periscan.config.category.general"))
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
						.option(Option.<Integer>createBuilder()
								.name(Component.translatable("periscan.config.scan_max_y"))
								.description(OptionDescription.of(Component.translatable("periscan.config.scan_max_y.desc")))
								.binding(defaults.scanMaxY,
										() -> config.scanMaxY,
										v -> config.scanMaxY = v)
								.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(-2032, 2031))
								.build())
						.option(Option.<String>createBuilder()
								.name(Component.translatable("periscan.config.schematics_folder"))
								.description(OptionDescription.of(Component.translatable("periscan.config.schematics_folder.desc")))
								.binding(defaults.schematicsFolder,
										() -> config.schematicsFolder,
										v -> config.schematicsFolder = v)
								.controller(StringControllerBuilder::create)
								.build())
						.option(Option.<PeriScanConfig.EdgeCorners>createBuilder()
								.name(Component.translatable("periscan.config.edge_corners"))
								.description(OptionDescription.of(Component.translatable("periscan.config.edge_corners.desc")))
								.binding(defaults.edgeCorners,
										() -> config.edgeCorners,
										v -> config.edgeCorners = v)
								.controller(opt -> EnumControllerBuilder.create(opt)
										.enumClass(PeriScanConfig.EdgeCorners.class)
										.formatValue(v -> Component.translatable(
												"periscan.config.edge_corners." + v.name().toLowerCase(Locale.ROOT))))
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Component.translatable("periscan.config.show_pending"))
								.description(OptionDescription.of(Component.translatable("periscan.config.show_pending.desc")))
								.binding(defaults.showPendingChunks,
										() -> config.showPendingChunks,
										v -> config.showPendingChunks = v)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Color>createBuilder()
								.name(Component.translatable("periscan.config.pending_color"))
								.binding(defaults.pendingChunkColor,
										() -> config.pendingChunkColor,
										v -> config.pendingChunkColor = v)
								.controller(ColorControllerBuilder::create)
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
						defaults.trenchOuterEnabled, () -> config.trenchOuterEnabled, v -> config.trenchOuterEnabled = v,
						defaults.trenchOuterBlocks, () -> config.trenchOuterBlocks, v -> config.trenchOuterBlocks = new ArrayList<>(v),
						defaults.trenchOuterWaterlogged, () -> config.trenchOuterWaterlogged, v -> config.trenchOuterWaterlogged = v,
						defaults.trenchOuterColor, () -> config.trenchOuterColor, v -> config.trenchOuterColor = v,
						List.of(),
						null))
				.category(zoneCategory("periscan.config.category.trench_inner",
						defaults.trenchInnerEnabled, () -> config.trenchInnerEnabled, v -> config.trenchInnerEnabled = v,
						defaults.trenchInnerBlocks, () -> config.trenchInnerBlocks, v -> config.trenchInnerBlocks = new ArrayList<>(v),
						defaults.trenchInnerWaterlogged, () -> config.trenchInnerWaterlogged, v -> config.trenchInnerWaterlogged = v,
						defaults.trenchInnerColor, () -> config.trenchInnerColor, v -> config.trenchInnerColor = v,
						List.of(Option.<Integer>createBuilder()
								.name(Component.translatable("periscan.config.falling_run"))
								.description(OptionDescription.of(Component.translatable("periscan.config.falling_run.desc")))
								.binding(defaults.trenchFallingRunLength,
										() -> config.trenchFallingRunLength,
										v -> config.trenchFallingRunLength = v)
								.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(2, 64).step(1))
								.build()),
						ListOption.<String>createBuilder()
								.name(Component.translatable("periscan.config.zone.fence_blocks"))
								.description(OptionDescription.of(Component.translatable("periscan.config.zone.fence_blocks.desc")))
								.binding(defaults.trenchInnerFenceBlocks,
										() -> config.trenchInnerFenceBlocks,
										v -> config.trenchInnerFenceBlocks = new ArrayList<>(v))
								.controller(StringControllerBuilder::create)
								.initial("minecraft:")
								.build()))
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("periscan.config.category.bottom_trench"))
						.option(Option.<Boolean>createBuilder()
								.name(Component.translatable("periscan.config.zone.enabled"))
								.description(OptionDescription.of(Component.translatable("periscan.config.bottom_trench.desc")))
								.binding(defaults.bottomTrenchEnabled,
										() -> config.bottomTrenchEnabled,
										v -> config.bottomTrenchEnabled = v)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Color>createBuilder()
								.name(Component.translatable("periscan.config.zone.color"))
								.binding(defaults.bottomTrenchColor,
										() -> config.bottomTrenchColor,
										v -> config.bottomTrenchColor = v)
								.controller(ColorControllerBuilder::create)
								.build())
						.build())
				.category(zoneCategory("periscan.config.category.eater",
						defaults.eaterEnabled, () -> config.eaterEnabled, v -> config.eaterEnabled = v,
						defaults.eaterBlocks, () -> config.eaterBlocks, v -> config.eaterBlocks = new ArrayList<>(v),
						defaults.eaterWaterlogged, () -> config.eaterWaterlogged, v -> config.eaterWaterlogged = v,
						defaults.eaterColor, () -> config.eaterColor, v -> config.eaterColor = v,
						List.of(Option.<Boolean>createBuilder()
								.name(Component.translatable("periscan.config.eater.include_trench"))
								.description(OptionDescription.of(Component.translatable("periscan.config.eater.include_trench.desc")))
								.binding(defaults.eaterIncludeTrench,
										() -> config.eaterIncludeTrench,
										v -> config.eaterIncludeTrench = v)
								.controller(TickBoxControllerBuilder::create)
								.build()),
						null))
				.save(() -> {
					PeriScanConfig.HANDLER.save();
					// Re-derive zones and rescan with the new settings if a region is active.
					ScanManager.INSTANCE.rescan();
				}))
				.generateScreen(parent);
	}

	private static ConfigCategory zoneCategory(String nameKey,
			boolean defaultEnabled, Supplier<Boolean> getEnabled, Consumer<Boolean> setEnabled,
			List<String> defaultBlocks, Supplier<List<String>> getBlocks, Consumer<List<String>> setBlocks,
			boolean defaultWaterlogged, Supplier<Boolean> getWaterlogged, Consumer<Boolean> setWaterlogged,
			Color defaultColor, Supplier<Color> getColor, Consumer<Color> setColor,
			List<Option<?>> extraOptions, ListOption<String> extraGroup) {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.translatable(nameKey))
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("periscan.config.zone.enabled"))
						.description(OptionDescription.of(Component.translatable("periscan.config.zone.enabled.desc")))
						.binding(defaultEnabled, getEnabled, setEnabled)
						.controller(TickBoxControllerBuilder::create)
						.build())
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
						.build());
		for (Option<?> extra : extraOptions) {
			builder.option(extra);
		}
		if (extraGroup != null) {
			builder.group(extraGroup);
		}
		return builder.build();
	}
}
