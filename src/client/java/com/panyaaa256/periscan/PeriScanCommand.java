package com.panyaaa256.periscan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.panyaaa256.periscan.config.PeriScanConfigScreen;
import com.panyaaa256.periscan.persist.RegionStore;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class PeriScanCommand {
	private PeriScanCommand() {
	}

	// Suggest the chunk the player is currently standing in.
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_CHUNK_X = (ctx, builder) -> {
		if (ctx.getSource().getPlayer() != null) {
			builder.suggest(ctx.getSource().getPlayer().chunkPosition().x);
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_CHUNK_Z = (ctx, builder) -> {
		if (ctx.getSource().getPlayer() != null) {
			builder.suggest(ctx.getSource().getPlayer().chunkPosition().z);
		}
		return builder.buildFuture();
	};

	public static void register() {
		// Only the full "start x1 z1 x2 z2" form has an executes(); with fewer
		// arguments brigadier fails with the vanilla "Unknown or incomplete command" error.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("periscan")
						.then(literal("start")
								.then(argument("x1", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_X)
										.then(argument("z1", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_Z)
												.then(argument("x2", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_X)
														.then(argument("z2", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_Z)
																.executes(PeriScanCommand::run))))))
						.then(literal("clear").executes(ctx -> clear(ctx.getSource())))
						.then(literal("reload").executes(ctx -> reload(ctx.getSource())))
						.then(literal("config").executes(ctx -> openConfig(ctx.getSource())))));
	}

	private static int run(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		ChunkPos a = new ChunkPos(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "z1"));
		ChunkPos b = new ChunkPos(IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "z2"));

		List<String> invalidEntries = ScanManager.INSTANCE.activate(a, b);
		RegionStore.save(a, b);

		int sizeX = (Math.abs(a.x - b.x) + 1) * 16;
		int sizeZ = (Math.abs(a.z - b.z) + 1) * 16;
		source.sendFeedback(Component.translatable("periscan.msg.activated", sizeX, sizeZ));
		warnInvalidEntries(source, invalidEntries);
		return 1;
	}

	private static int clear(FabricClientCommandSource source) {
		ScanManager.INSTANCE.deactivate();
		RegionStore.delete();
		source.sendFeedback(Component.translatable("periscan.msg.cleared"));
		return 1;
	}

	private static int reload(FabricClientCommandSource source) {
		if (!ScanManager.INSTANCE.hasRegion()) {
			source.sendError(Component.translatable("periscan.msg.no_region"));
			return 0;
		}
		// Also starts scanning for a region restored on login (kept dormant until now).
		List<String> invalidEntries = ScanManager.INSTANCE.activate(ScanManager.INSTANCE.cornerA(), ScanManager.INSTANCE.cornerB());
		source.sendFeedback(Component.translatable("periscan.msg.reloaded"));
		warnInvalidEntries(source, invalidEntries);
		return 1;
	}

	private static int openConfig(FabricClientCommandSource source) {
		PeriScanClient.scheduleScreen(() -> PeriScanConfigScreen.create(null));
		return 1;
	}

	private static void warnInvalidEntries(FabricClientCommandSource source, List<String> invalidEntries) {
		for (String entry : invalidEntries) {
			source.sendFeedback(Component.translatable("periscan.msg.invalid_entry", entry));
		}
	}
}
