package com.panyaaa256.periscan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.config.PeriScanConfigScreen;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.Result;
import com.panyaaa256.periscan.persist.PeriProfile;
import com.panyaaa256.periscan.persist.ProfileStore;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The /peri command: peri profiles (add/remove/list), scanning bound to them
 * (scan start/clear/reload), litematica schematic placement (schematic) and
 * the config screen (config).
 */
public final class PeriCommand {
	private PeriCommand() {
	}

	private static final String SCHEMATIC_EXTENSION = ".litematic";

	// Suggest the chunk the player is currently standing in.
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_CHUNK_X = (ctx, builder) -> {
		if (ctx.getSource().getPlayer() != null) {
			builder.suggest(ctx.getSource().getPlayer().chunkPosition().x());
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_CHUNK_Z = (ctx, builder) -> {
		if (ctx.getSource().getPlayer() != null) {
			builder.suggest(ctx.getSource().getPlayer().chunkPosition().z());
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_PROFILE = (ctx, builder) -> {
		for (String name : ProfileStore.profiles().keySet()) {
			if (name.startsWith(builder.getRemaining())) {
				builder.suggest(name);
			}
		}
		return builder.buildFuture();
	};
	// Suggest the schematic set folders that actually exist under the peri root.
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_SET_DIR = (ctx, builder) -> {
		if (LitematicaIntegration.isAvailable() && Files.isDirectory(periRoot())) {
			try (Stream<Path> dirs = Files.list(periRoot())) {
				dirs.filter(Files::isDirectory)
						.map(dir -> dir.getFileName().toString())
						.filter(name -> name.startsWith(builder.getRemaining()))
						.forEach(builder::suggest);
			} catch (IOException ignored) {
			}
		}
		return builder.buildFuture();
	};

	public static void register() {
		// Only the complete forms have an executes(); with fewer arguments
		// brigadier fails with the vanilla "Unknown or incomplete command" error.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("peri")
						.then(literal("add")
								.then(argument("x1", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_X)
										.then(argument("z1", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_Z)
												.then(argument("x2", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_X)
														.then(argument("z2", IntegerArgumentType.integer()).suggests(SUGGEST_CHUNK_Z)
																.then(argument("name", StringArgumentType.word())
																		.executes(PeriCommand::add)))))))
						.then(literal("remove")
								.then(argument("name", StringArgumentType.word()).suggests(SUGGEST_PROFILE)
										.executes(PeriCommand::remove)))
						.then(literal("list").executes(ctx -> list(ctx.getSource())))
						.then(literal("scan")
								.then(literal("start")
										.then(argument("name", StringArgumentType.word()).suggests(SUGGEST_PROFILE)
												.executes(PeriCommand::scanStart)))
								.then(literal("clear").executes(ctx -> scanClear(ctx.getSource())))
								.then(literal("reload").executes(ctx -> scanReload(ctx.getSource()))))
						.then(literal("schematic")
								.then(argument("name", StringArgumentType.word()).suggests(SUGGEST_PROFILE)
										.executes(ctx -> schematic(ctx, null))
										.then(argument("dir", StringArgumentType.word()).suggests(SUGGEST_SET_DIR)
												.executes(ctx -> schematic(ctx, StringArgumentType.getString(ctx, "dir"))))))
						.then(literal("config").executes(ctx -> openConfig()))));
	}

	/** Peris are never built in the end; fail fast so the mistake is obvious. */
	private static boolean rejectEnd(FabricClientCommandSource source) {
		if (source.getLevel().dimension() == Level.END) {
			source.sendError(Component.translatable("periscan.msg.end_not_supported"));
			return true;
		}
		return false;
	}

	/** The named profile, or null after sending the "no such profile" error. */
	private static PeriProfile findProfile(FabricClientCommandSource source, String name) {
		PeriProfile profile = ProfileStore.get(name);
		if (profile == null) {
			source.sendError(Component.translatable("periscan.msg.no_profile", name));
		}
		return profile;
	}

	/** Profiles only work in the dimension they were created in. */
	private static boolean rejectOtherDimension(FabricClientCommandSource source, PeriProfile profile) {
		if (!profile.dimension().equals(dimensionId(source))) {
			source.sendError(Component.translatable("periscan.msg.wrong_dimension", profile.name(), profile.dimension()));
			return true;
		}
		return false;
	}

	private static String dimensionId(FabricClientCommandSource source) {
		return source.getLevel().dimension().identifier().toString();
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		if (rejectEnd(source)) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		if (ProfileStore.get(name) != null) {
			source.sendError(Component.translatable("periscan.msg.profile_exists", name));
			return 0;
		}
		ChunkPos a = new ChunkPos(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "z1"));
		ChunkPos b = new ChunkPos(IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "z2"));
		// The profile is bound to the dimension the command was run in.
		PeriProfile profile = PeriProfile.of(name, a, b, dimensionId(source));
		ProfileStore.put(profile);
		source.sendFeedback(Component.translatable("periscan.msg.profile_added",
				name, profile.sizeBlocksX(), profile.sizeBlocksZ()));
		return 1;
	}

	private static int remove(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		if (findProfile(source, name) == null) {
			return 0;
		}
		if (name.equals(ProfileStore.lastScanned())) {
			ScanManager.INSTANCE.deactivate();
		}
		ProfileStore.remove(name);
		source.sendFeedback(Component.translatable("periscan.msg.profile_removed", name));
		if (LitematicaIntegration.isAvailable()) {
			int removed = LitematicaIntegration.removePlacements(placementPrefix(name));
			if (removed > 0) {
				source.sendFeedback(Component.translatable("periscan.msg.placements_removed", removed));
			}
		}
		return 1;
	}

	private static int list(FabricClientCommandSource source) {
		Map<String, PeriProfile> profiles = ProfileStore.profiles();
		if (profiles.isEmpty()) {
			source.sendFeedback(Component.translatable("periscan.msg.no_profiles"));
			return 1;
		}
		String last = ProfileStore.lastScanned();
		source.sendFeedback(Component.translatable("periscan.msg.profile_list_header", profiles.size()));
		for (PeriProfile profile : profiles.values()) {
			Component line = Component.translatable("periscan.msg.profile_list_entry",
					profile.name(), profile.minX(), profile.minZ(), profile.maxX(), profile.maxZ(),
					profile.sizeBlocksX(), profile.sizeBlocksZ(), profile.dimension());
			if (profile.name().equals(last)) {
				line = Component.empty().append(line)
						.append(Component.translatable("periscan.msg.profile_list_active"));
			}
			source.sendFeedback(line);
		}
		return 1;
	}

	private static int scanStart(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		PeriProfile profile = findProfile(source, name);
		if (profile == null) {
			return 0;
		}
		if (startScan(source, profile)) {
			ProfileStore.setLastScanned(name);
			source.sendFeedback(Component.translatable("periscan.msg.scan_started",
					name, profile.sizeBlocksX(), profile.sizeBlocksZ()));
			return 1;
		}
		return 0;
	}

	private static int scanClear(FabricClientCommandSource source) {
		ScanManager.INSTANCE.deactivate();
		ProfileStore.clearLastScanned();
		source.sendFeedback(Component.translatable("periscan.msg.cleared"));
		return 1;
	}

	private static int scanReload(FabricClientCommandSource source) {
		String last = ProfileStore.lastScanned();
		PeriProfile profile = last == null ? null : ProfileStore.get(last);
		if (profile == null) {
			source.sendError(Component.translatable("periscan.msg.no_last_profile"));
			return 0;
		}
		// Also starts scanning for a profile restored on login (kept dormant until now).
		if (startScan(source, profile)) {
			source.sendFeedback(Component.translatable("periscan.msg.reloaded", profile.name()));
			return 1;
		}
		return 0;
	}

	/**
	 * Shared validation (zones enabled, right dimension) and activation for
	 * scan start / reload. Returns false after sending an error.
	 */
	private static boolean startScan(FabricClientCommandSource source, PeriProfile profile) {
		if (rejectEnd(source)) {
			return false;
		}
		if (!PeriScanConfig.anyZoneEnabled()) {
			source.sendError(Component.translatable("periscan.msg.all_disabled"));
			return false;
		}
		if (rejectOtherDimension(source, profile)) {
			return false;
		}
		List<String> invalidEntries = ScanManager.INSTANCE.activate(
				source.getLevel().dimension(), profile.minChunk(), profile.maxChunk());
		for (String entry : invalidEntries) {
			source.sendFeedback(Component.translatable("periscan.msg.invalid_entry", entry));
		}
		return true;
	}

	/**
	 * Creates litematica placements for every schematic of a set directory:
	 * all/ files once at the min corner, edge/ files as a pair on the corner
	 * pair chosen in the config (unrotated + 180deg, or X-mirror + Z-mirror).
	 * Existing placements of the profile are replaced; a broken file aborts
	 * before anything is touched.
	 */
	private static int schematic(CommandContext<FabricClientCommandSource> ctx, String dirArg) {
		FabricClientCommandSource source = ctx.getSource();
		if (rejectEnd(source)) {
			return 0;
		}
		if (!LitematicaIntegration.isAvailable()) {
			source.sendError(Component.translatable("periscan.msg.litematica_missing"));
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		PeriProfile profile = findProfile(source, name);
		if (profile == null || rejectOtherDimension(source, profile)) {
			return 0;
		}
		PeriDimension dimension = PeriDimension.of(profile.dimension());
		if (dimension == null) {
			source.sendError(Component.translatable("periscan.msg.unsupported_dimension", profile.dimension()));
			return 0;
		}
		int originY = dimension.schematicOriginY();

		String dir = dirArg != null ? dirArg : dimension.defaultSetDir();
		Path setDir = periRoot().resolve(dir);
		if (!Files.isDirectory(setDir)) {
			source.sendError(Component.translatable("periscan.msg.schem_dir_missing", setDir.toString()));
			return 0;
		}
		Path allDir = setDir.resolve("all");
		Path edgeDir = setDir.resolve("edge");
		// Nether trenchers always launch from a fixed compass direction, so the
		// opposite edge copy must be mirrored, never rotated 180deg (a rotation
		// reverses the launch direction). Which axis flips cannot be derived from
		// the file, so nether sets pre-assign it via the mx / mz subfolders.
		boolean nether = dimension == PeriDimension.NETHER;
		List<String> allFiles = listSchematics(allDir);
		List<String> edgeFiles = nether ? List.of() : listSchematics(edgeDir);
		List<String> edgeMxFiles = nether ? listSchematics(edgeDir.resolve("mx")) : List.of();
		List<String> edgeMzFiles = nether ? listSchematics(edgeDir.resolve("mz")) : List.of();
		if (nether && !listSchematics(edgeDir).isEmpty()) {
			source.sendError(Component.translatable("periscan.msg.schem_edge_unsorted", edgeDir.toString()));
			return 0;
		}
		if (allFiles.isEmpty() && edgeFiles.isEmpty() && edgeMxFiles.isEmpty() && edgeMzFiles.isEmpty()) {
			source.sendError(Component.translatable("periscan.msg.schem_no_files", setDir.toString()));
			return 0;
		}

		// The four corner blocks of the perimeter at the origin height.
		int minBlockX = profile.minBlockX();
		int minBlockZ = profile.minBlockZ();
		int maxBlockX = profile.maxBlockX();
		int maxBlockZ = profile.maxBlockZ();
		BlockPos minCorner = new BlockPos(minBlockX, originY, minBlockZ);
		String prefix = placementPrefix(name);
		List<PlannedPlacement> plan = new ArrayList<>();
		for (String file : allFiles) {
			plan.add(new PlannedPlacement(allDir, file,
					prefix + "all/" + stripExtension(file), minCorner, Rotation.NONE, Mirror.NONE));
		}
		// Mirrored content extends away from its corner: FRONT_BACK flips X so it
		// covers -x/+z from the +x/-z corner, LEFT_RIGHT flips Z covering +x/-z
		// from the -x/+z corner.
		for (String file : edgeMxFiles) {
			String base = prefix + "edge/mx/" + stripExtension(file);
			plan.add(new PlannedPlacement(edgeDir.resolve("mx"), file, base, minCorner, Rotation.NONE, Mirror.NONE));
			plan.add(new PlannedPlacement(edgeDir.resolve("mx"), file, base + "@mx",
					new BlockPos(maxBlockX, originY, minBlockZ), Rotation.NONE, Mirror.FRONT_BACK));
		}
		for (String file : edgeMzFiles) {
			String base = prefix + "edge/mz/" + stripExtension(file);
			plan.add(new PlannedPlacement(edgeDir.resolve("mz"), file, base, minCorner, Rotation.NONE, Mirror.NONE));
			plan.add(new PlannedPlacement(edgeDir.resolve("mz"), file, base + "@mz",
					new BlockPos(minBlockX, originY, maxBlockZ), Rotation.NONE, Mirror.LEFT_RIGHT));
		}
		for (String file : edgeFiles) {
			String base = prefix + "edge/" + stripExtension(file);
			if (PeriScanConfig.get().edgeCorners == PeriScanConfig.EdgeCorners.PM_MP) {
				// Mirrors, not 90/270 rotations: a rotation would swap the NS/EW
				// trench widths.
				plan.add(new PlannedPlacement(edgeDir, file, base + "@mx",
						new BlockPos(maxBlockX, originY, minBlockZ), Rotation.NONE, Mirror.FRONT_BACK));
				plan.add(new PlannedPlacement(edgeDir, file, base + "@mz",
						new BlockPos(minBlockX, originY, maxBlockZ), Rotation.NONE, Mirror.LEFT_RIGHT));
			} else {
				plan.add(new PlannedPlacement(edgeDir, file, base, minCorner, Rotation.NONE, Mirror.NONE));
				plan.add(new PlannedPlacement(edgeDir, file, base + "@180",
						new BlockPos(maxBlockX, originY, maxBlockZ), Rotation.CLOCKWISE_180, Mirror.NONE));
			}
		}

		Result result = LitematicaIntegration.place(plan, prefix);
		if (result.failedFile() != null) {
			source.sendError(Component.translatable("periscan.msg.schem_load_failed", result.failedFile()));
			return 0;
		}
		source.sendFeedback(Component.translatable("periscan.msg.schem_done",
				name, result.created(),
				allFiles.size() + edgeFiles.size() + edgeMxFiles.size() + edgeMzFiles.size()));
		if (result.removed() > 0) {
			source.sendFeedback(Component.translatable("periscan.msg.schem_replaced", result.removed()));
		}
		return 1;
	}

	/** All placements of a profile share this name prefix; replacement and removal match on it. */
	private static String placementPrefix(String profileName) {
		return "peri/" + profileName + "/";
	}

	private static Path periRoot() {
		return LitematicaIntegration.schematicsBaseDirectory().resolve(PeriScanConfig.get().schematicsFolder);
	}

	private static List<String> listSchematics(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
					.filter(fileName -> fileName.endsWith(SCHEMATIC_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static String stripExtension(String fileName) {
		return fileName.substring(0, fileName.length() - SCHEMATIC_EXTENSION.length());
	}

	private static int openConfig() {
		PeriScanClient.scheduleScreen(() -> PeriScanConfigScreen.create(null));
		return 1;
	}
}
