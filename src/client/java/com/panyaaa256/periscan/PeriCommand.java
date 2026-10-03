package com.panyaaa256.periscan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.panyaaa256.periscan.compat.VersionCompat;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.config.PeriScanConfigScreen;
import com.panyaaa256.periscan.config.SchematicProfileScreen;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.Result;
import com.panyaaa256.periscan.persist.PeriProfile;
import com.panyaaa256.periscan.persist.ProfileStore;
import com.panyaaa256.periscan.scan.ScanManager;
import com.panyaaa256.periscan.schematic.SchematicEntry;
import com.panyaaa256.periscan.schematic.SchematicPlanner;
import com.panyaaa256.periscan.schematic.SchematicProfile;
import com.panyaaa256.periscan.schematic.SchematicProfileStore;
import com.panyaaa256.periscan.schematic.SchematicProfileStore.UpdateResult;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The /peri command: peri profiles (add/remove/list), scanning bound to them
 * (scan start/clear/reload), schematic profiles and their litematica
 * placement (schematic create/edit/place/clear/list/copy/remove) and the config
 * screen (config).
 */
public final class PeriCommand {
	private PeriCommand() {
	}

	// Chunk coordinates of /peri add, limited to the chunks inside the world.
	private static final IntegerArgumentType CHUNK_COORD =
			IntegerArgumentType.integer(-PeriProfile.MAX_CHUNK_COORD, PeriProfile.MAX_CHUNK_COORD);

	// Suggest the chunk the player is currently standing in.
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_CHUNK_X = (ctx, builder) -> {
		if (ctx.getSource().getPlayer() != null) {
			builder.suggest(VersionCompat.chunkX(ctx.getSource().getPlayer().chunkPosition()));
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_CHUNK_Z = (ctx, builder) -> {
		if (ctx.getSource().getPlayer() != null) {
			builder.suggest(VersionCompat.chunkZ(ctx.getSource().getPlayer().chunkPosition()));
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
	private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_SCHEMATIC_PROFILE = (ctx, builder) -> {
		for (String name : SchematicProfileStore.names(SchematicProfileStore.root())) {
			if (name.startsWith(builder.getRemaining())) {
				builder.suggest(name);
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
								.then(argument("x1", CHUNK_COORD).suggests(SUGGEST_CHUNK_X)
										.then(argument("z1", CHUNK_COORD).suggests(SUGGEST_CHUNK_Z)
												.then(argument("x2", CHUNK_COORD).suggests(SUGGEST_CHUNK_X)
														.then(argument("z2", CHUNK_COORD).suggests(SUGGEST_CHUNK_Z)
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
								.then(literal("create")
										.then(argument("profile", StringArgumentType.word())
												.executes(PeriCommand::schematicCreate)))
								.then(literal("edit")
										.then(argument("profile", StringArgumentType.word()).suggests(SUGGEST_SCHEMATIC_PROFILE)
												.executes(PeriCommand::schematicEdit)))
								.then(literal("place")
										.then(argument("name", StringArgumentType.word()).suggests(SUGGEST_PROFILE)
												.then(argument("profile", StringArgumentType.word()).suggests(SUGGEST_SCHEMATIC_PROFILE)
														.executes(PeriCommand::schematicPlace))))
								.then(literal("clear")
										.then(argument("name", StringArgumentType.word()).suggests(SUGGEST_PROFILE)
												.executes(PeriCommand::schematicClear)))
								.then(literal("list").executes(ctx -> schematicList(ctx.getSource())))
								.then(literal("copy")
										.then(argument("from", StringArgumentType.word()).suggests(SUGGEST_SCHEMATIC_PROFILE)
												.then(argument("to", StringArgumentType.word())
														.executes(PeriCommand::schematicCopy))))
								.then(literal("remove")
										.then(argument("profile", StringArgumentType.word()).suggests(SUGGEST_SCHEMATIC_PROFILE)
												.executes(PeriCommand::schematicRemove))))
						.then(literal("config").executes(ctx -> openConfig()))));
	}

	/** Peris are never built in the end; fail fast so the mistake is obvious. */
	private static boolean rejectEnd(ClientLevel level, Consumer<Component> error) {
		if (level.dimension() == Level.END) {
			error.accept(Component.translatable("periscan.msg.end_not_supported"));
			return true;
		}
		return false;
	}

	/** Rejects perimeters too large to scan; see {@link PeriProfile#MAX_SIDE_CHUNKS}. */
	private static boolean rejectTooLarge(Consumer<Component> error, PeriProfile profile) {
		if (profile.exceedsMaxSize()) {
			error.accept(Component.translatable("periscan.msg.region_too_large",
					profile.sideChunksX(), profile.sideChunksZ(), PeriProfile.MAX_SIDE_CHUNKS));
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
	private static boolean rejectOtherDimension(ClientLevel level, Consumer<Component> error, PeriProfile profile) {
		if (!profile.dimension().equals(dimensionId(level))) {
			error.accept(Component.translatable("periscan.msg.wrong_dimension", profile.name(), profile.dimension()));
			return true;
		}
		return false;
	}

	private static String dimensionId(ClientLevel level) {
		return level.dimension().identifier().toString();
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		if (rejectEnd(source.getLevel(), source::sendError)) {
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
		PeriProfile profile = PeriProfile.of(name, a, b, dimensionId(source.getLevel()));
		if (rejectTooLarge(source::sendError, profile)) {
			return 0;
		}
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
			int removed = LitematicaIntegration.removePlacements(SchematicPlanner.placementPrefix(name));
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
		if (startScan(source.getLevel(), source::sendFeedback, source::sendError, profile)) {
			ProfileStore.setLastScanned(name);
			source.sendFeedback(Component.translatable("periscan.msg.scan_started",
					name, profile.sizeBlocksX(), profile.sizeBlocksZ()));
			return 1;
		}
		return 0;
	}

	private static int scanClear(FabricClientCommandSource source) {
		scanClear(source::sendFeedback);
		return 1;
	}

	/** /peri scan clear; also run by a key (see PeriKeybinds), which has no command source. */
	static void scanClear(Consumer<Component> feedback) {
		ScanManager.INSTANCE.deactivate();
		ProfileStore.clearLastScanned();
		feedback.accept(Component.translatable("periscan.msg.cleared"));
	}

	private static int scanReload(FabricClientCommandSource source) {
		return scanReload(source.getLevel(), source::sendFeedback, source::sendError) ? 1 : 0;
	}

	/** /peri scan reload; also run by a key (see PeriKeybinds), which has no command source. */
	static boolean scanReload(ClientLevel level, Consumer<Component> feedback, Consumer<Component> error) {
		String last = ProfileStore.lastScanned();
		PeriProfile profile = last == null ? null : ProfileStore.get(last);
		if (profile == null) {
			error.accept(Component.translatable("periscan.msg.no_last_profile"));
			return false;
		}
		// Also starts scanning for a profile restored on login (kept dormant until now).
		if (startScan(level, feedback, error, profile)) {
			feedback.accept(Component.translatable("periscan.msg.reloaded", profile.name()));
			return true;
		}
		return false;
	}

	/**
	 * Shared validation (zones enabled, right dimension) and activation for
	 * scan start / reload. Returns false after sending an error.
	 */
	private static boolean startScan(ClientLevel level, Consumer<Component> feedback, Consumer<Component> error,
			PeriProfile profile) {
		if (rejectEnd(level, error)) {
			return false;
		}
		if (!PeriScanConfig.anyZoneEnabled()) {
			error.accept(Component.translatable("periscan.msg.all_disabled"));
			return false;
		}
		if (rejectOtherDimension(level, error, profile)) {
			return false;
		}
		// Profiles are checked on add, but files may have been edited by hand.
		if (rejectTooLarge(error, profile)) {
			return false;
		}
		List<Component> problems = ScanManager.INSTANCE.activate(
				level.dimension(), profile.minChunk(), profile.maxChunk());
		for (Component problem : problems) {
			feedback.accept(problem);
		}
		return true;
	}

	private static boolean rejectWithoutLitematica(FabricClientCommandSource source) {
		if (!LitematicaIntegration.isAvailable()) {
			source.sendError(Component.translatable("periscan.msg.litematica_missing"));
			return true;
		}
		return false;
	}

	/**
	 * Creates an empty schematic profile and opens its settings screen. The
	 * profile exists from now on, even if the screen is closed without saving.
	 */
	private static int schematicCreate(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		// Checked first: without litematica the profile could not be filled.
		if (rejectWithoutLitematica(source)) {
			return 0;
		}
		String schematicProfile = StringArgumentType.getString(ctx, "profile");
		if (!SchematicProfileStore.isValidName(schematicProfile)) {
			source.sendError(Component.translatable("periscan.msg.schem_profile_invalid_name", schematicProfile));
			return 0;
		}
		Path root = SchematicProfileStore.root();
		if (SchematicProfileStore.exists(root, schematicProfile)) {
			source.sendError(Component.translatable("periscan.msg.schem_profile_exists", schematicProfile));
			return 0;
		}
		SchematicProfileStore.update(root, schematicProfile, SchematicProfile.EMPTY, List.of());
		// update() only logs a failed write, so check that the profile is there.
		if (!SchematicProfileStore.exists(root, schematicProfile)) {
			source.sendError(Component.translatable("periscan.msg.schem_profile_create_failed", schematicProfile));
			return 0;
		}
		source.sendFeedback(Component.translatable("periscan.msg.schem_profile_created", schematicProfile));
		openSchematicProfileScreen(schematicProfile);
		return 1;
	}

	/** Opens the settings screen of an existing schematic profile; new ones are made with create. */
	private static int schematicEdit(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		// Schematics are imported from litematica's schematics folder.
		if (rejectWithoutLitematica(source)) {
			return 0;
		}
		String schematicProfile = StringArgumentType.getString(ctx, "profile");
		if (!SchematicProfileStore.exists(SchematicProfileStore.root(), schematicProfile)) {
			source.sendError(Component.translatable("periscan.msg.no_schem_profile", schematicProfile));
			return 0;
		}
		openSchematicProfileScreen(schematicProfile);
		return 1;
	}

	private static void openSchematicProfileScreen(String schematicProfile) {
		Path sourceRoot = LitematicaIntegration.schematicsBaseDirectory();
		PeriScanClient.scheduleScreen(() -> SchematicProfileScreen.create(null, schematicProfile, sourceRoot));
	}

	/**
	 * Creates litematica placements for the schematics of a schematic profile
	 * (see SchematicPlanner). Existing placements of the peri profile are
	 * replaced; a broken file aborts before anything is touched.
	 */
	private static int schematicPlace(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		if (rejectEnd(source.getLevel(), source::sendError) || rejectWithoutLitematica(source)) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		PeriProfile profile = findProfile(source, name);
		if (profile == null || rejectOtherDimension(source.getLevel(), source::sendError, profile)) {
			return 0;
		}
		String schematicProfile = StringArgumentType.getString(ctx, "profile");
		Path root = SchematicProfileStore.root();
		if (!SchematicProfileStore.exists(root, schematicProfile)) {
			source.sendError(Component.translatable("periscan.msg.no_schem_profile", schematicProfile));
			return 0;
		}

		List<PlannedPlacement> plan = SchematicPlanner.plan(SchematicProfileStore.dir(root, schematicProfile),
				SchematicProfileStore.load(root, schematicProfile).entries(), profile);
		if (plan.isEmpty()) {
			source.sendError(Component.translatable("periscan.msg.schem_no_files", schematicProfile));
			return 0;
		}
		Result result = LitematicaIntegration.place(plan, SchematicPlanner.placementPrefix(name));
		if (result.failedFile() != null) {
			source.sendError(Component.translatable("periscan.msg.schem_load_failed", result.failedFile()));
			return 0;
		}
		source.sendFeedback(Component.translatable("periscan.msg.schem_done", name, result.created(), schematicProfile));
		if (result.removed() > 0) {
			source.sendFeedback(Component.translatable("periscan.msg.schem_replaced", result.removed()));
		}
		return 1;
	}

	/** Removes only the litematica placements made for a peri profile; both profiles are kept. */
	private static int schematicClear(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		if (rejectWithoutLitematica(source)) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		if (findProfile(source, name) == null) {
			return 0;
		}
		int removed = LitematicaIntegration.removePlacements(SchematicPlanner.placementPrefix(name));
		if (removed > 0) {
			source.sendFeedback(Component.translatable("periscan.msg.schem_cleared", name, removed));
		} else {
			source.sendFeedback(Component.translatable("periscan.msg.schem_clear_none", name));
		}
		return 1;
	}

	private static int schematicList(FabricClientCommandSource source) {
		Path root = SchematicProfileStore.root();
		List<String> names = SchematicProfileStore.names(root);
		if (names.isEmpty()) {
			source.sendFeedback(Component.translatable("periscan.msg.no_schem_profiles"));
			return 1;
		}
		source.sendFeedback(Component.translatable("periscan.msg.schem_profile_list_header", names.size()));
		for (String name : names) {
			List<SchematicEntry> entries = SchematicProfileStore.load(root, name).entries();
			source.sendFeedback(Component.translatable("periscan.msg.schem_profile_list_entry", name, entries.size()));
		}
		return 1;
	}

	/** Duplicates a schematic profile together with its schematic copies, e.g. to reuse an overworld one for the nether. */
	private static int schematicCopy(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String from = StringArgumentType.getString(ctx, "from");
		String to = StringArgumentType.getString(ctx, "to");
		Path root = SchematicProfileStore.root();
		if (!SchematicProfileStore.exists(root, from)) {
			source.sendError(Component.translatable("periscan.msg.no_schem_profile", from));
			return 0;
		}
		if (!SchematicProfileStore.isValidName(to)) {
			source.sendError(Component.translatable("periscan.msg.schem_profile_invalid_name", to));
			return 0;
		}
		if (SchematicProfileStore.exists(root, to)) {
			source.sendError(Component.translatable("periscan.msg.schem_profile_exists", to));
			return 0;
		}
		UpdateResult result = SchematicProfileStore.copy(root, from, to);
		source.sendFeedback(Component.translatable("periscan.msg.schem_profile_copied",
				from, to, result.profile().entries().size()));
		// The profile is created anyway; tell which schematic files did not make it.
		if (!result.failedImports().isEmpty()) {
			String files = result.failedImports().stream()
					.map(path -> path.getFileName().toString())
					.collect(Collectors.joining(", "));
			source.sendError(Component.translatable("periscan.msg.schem_copy_failed",
					result.failedImports().size(), files));
		}
		return 1;
	}

	/** Deletes a schematic profile with its schematic copies; placements made from it are kept. */
	private static int schematicRemove(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String schematicProfile = StringArgumentType.getString(ctx, "profile");
		if (!SchematicProfileStore.remove(SchematicProfileStore.root(), schematicProfile)) {
			source.sendError(Component.translatable("periscan.msg.no_schem_profile", schematicProfile));
			return 0;
		}
		source.sendFeedback(Component.translatable("periscan.msg.schem_profile_removed", schematicProfile));
		return 1;
	}

	private static int openConfig() {
		PeriScanClient.scheduleScreen(() -> PeriScanConfigScreen.create(null));
		return 1;
	}
}
