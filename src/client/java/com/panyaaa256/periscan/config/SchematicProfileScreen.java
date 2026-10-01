package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.compat.VersionCompat;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration;
import com.panyaaa256.periscan.schematic.Corner;
import com.panyaaa256.periscan.schematic.SchematicEntry;
import com.panyaaa256.periscan.schematic.SchematicProfile;
import com.panyaaa256.periscan.schematic.SchematicProfileStore;
import com.panyaaa256.periscan.schematic.SchematicProfileStore.UpdateResult;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
//? if <1.20
//import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The settings screen of one schematic profile: a list of files to import, and
 * a group per schematic with its corners and origin height. YACL screens
 * cannot grow groups while open, so imported schematics get their group when
 * the screen is rebuilt after saving.
 */
public final class SchematicProfileScreen {
	private static final String KEY = "periscan.schematic.";
	// Failed imports listed in the toast before "...".
	private static final int MAX_TOAST_ENTRIES = 5;

	/** The values of one schematic while the screen is open. */
	private static final class EntryState {
		final String fileName;
		final Set<Corner> corners;
		// The saved values when the screen opened, the "Reset" targets.
		final Set<Corner> initialCorners;
		final int initialOriginY;
		int originY;
		boolean remove;

		EntryState(SchematicEntry entry) {
			this.fileName = entry.fileName();
			this.corners = entry.corners().isEmpty() ? EnumSet.noneOf(Corner.class) : EnumSet.copyOf(entry.corners());
			this.originY = entry.originY();
			this.initialCorners = EnumSet.copyOf(this.corners);
			this.initialOriginY = this.originY;
		}
	}

	private SchematicProfileScreen() {
	}

	/**
	 * @param sourceRoot folder the import list picks files from (litematica's
	 *                   schematics folder); its entries are relative to it
	 */
	public static Screen create(Screen parent, String name, Path sourceRoot) {
		SchematicProfile profile = SchematicProfileStore.load(SchematicProfileStore.root(), name);
		List<EntryState> states = profile.entries().stream()
				.map(EntryState::new)
				.toList();
		List<String> imports = new ArrayList<>();

		ConfigCategory.Builder category = ConfigCategory.createBuilder()
				.name(Component.literal(name))
				.group(importList(listSources(sourceRoot), imports));
		Path profileDir = SchematicProfileStore.dir(SchematicProfileStore.root(), name);
		for (EntryState state : states) {
			category.group(entryGroup(state, LitematicaIntegration.schematicName(profileDir, state.fileName)));
		}
		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable(KEY + "title", name))
				.category(category.build())
				.save(() -> save(parent, name, sourceRoot, profile.defaultOriginY(), states, imports))
				.build()
				.generateScreen(parent);
	}

	private static void save(Screen parent, String name, Path sourceRoot, int defaultOriginY, List<EntryState> states,
			List<String> imports) {
		List<SchematicEntry> kept = states.stream()
				.filter(state -> !state.remove)
				.map(state -> new SchematicEntry(state.fileName, state.corners, state.originY))
				.toList();
		List<Path> sources = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		for (String entry : imports) {
			if (entry.isBlank()) {
				continue;
			}
			try {
				sources.add(sourceRoot.resolve(entry.trim()));
			} catch (InvalidPathException e) {
				failed.add(entry.trim());
			}
		}
		UpdateResult result = SchematicProfileStore.update(SchematicProfileStore.root(), name,
				new SchematicProfile(defaultOriginY, kept), sources);
		result.failedImports().forEach(source -> failed.add(source.getFileName().toString()));
		if (!failed.isEmpty()) {
			List<String> shown = failed.subList(0, Math.min(failed.size(), MAX_TOAST_ENTRIES));
			String list = String.join(", ", shown) + (failed.size() > shown.size() ? ", ..." : "");
			VersionCompat.showToast(Component.translatable(KEY + "import_failed.title"),
					Component.translatable(KEY + "import_failed.message", failed.size(), list));
		}
		// Rebuild so imported schematics get their group and removed ones lose it.
		if (!imports.isEmpty() || kept.size() != states.size()) {
			Minecraft client = Minecraft.getInstance();
			client.gui.setScreen(create(parent, name, sourceRoot));
		}
	}

	/** The .litematic files under root as "/"-separated relative paths, sorted; empty if unreadable. */
	private static List<String> listSources(Path root) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(Files::isRegularFile)
					.filter(file -> SchematicEntry.isValidFileName(file.getFileName().toString()))
					.map(file -> root.relativize(file).toString().replace('\\', '/'))
					.sorted()
					.toList();
		} catch (IOException | RuntimeException e) {
			return List.of();
		}
	}

	/**
	 * The files to copy into the profile on save, picked from {@code sources}
	 * (typing filters them). 1.19.4's YACL has no dropdown controller, so there
	 * the path is typed.
	 */
	private static ListOption<String> importList(List<String> sources, List<String> imports) {
		return ListOption.<String>createBuilder()
				.name(Component.translatable(KEY + "import"))
				.description(OptionDescription.of(Component.translatable(KEY + "import.desc")))
				.binding(List.of(), () -> List.copyOf(imports), v -> {
					imports.clear();
					imports.addAll(v);
				})
				//? if >=1.20 {
				.customController(opt -> new PickingDropdownController(opt, sources, false))
				//?} else
				//.controller(StringControllerBuilder::create)
				.initial("")
				.build();
	}

	/**
	 * @param schematicName the name in the schematic's metadata, shown as the
	 *                      group's title; null to show the file name
	 */
	private static OptionGroup entryGroup(EntryState state, String schematicName) {
		OptionGroup.Builder group = OptionGroup.createBuilder()
				.name(Component.literal(schematicName != null ? schematicName : state.fileName))
				.description(OptionDescription.of(Component.literal(state.fileName)));
		for (Corner corner : Corner.values()) {
			group.option(Option.<Boolean>createBuilder()
					.name(Component.translatable(KEY + "corner." + corner.name().toLowerCase(Locale.ROOT)))
					.description(OptionDescription.of(Component.translatable(KEY + "corner.desc")))
					.binding(state.initialCorners.contains(corner), () -> state.corners.contains(corner), v -> {
						if (v) {
							state.corners.add(corner);
						} else {
							state.corners.remove(corner);
						}
					})
					.controller(TickBoxControllerBuilder::create)
					.build());
		}
		return group
				.option(Option.<Integer>createBuilder()
						.name(Component.translatable(KEY + "origin_y"))
						.description(OptionDescription.of(Component.translatable(KEY + "origin_y.desc")))
						.binding(state.initialOriginY, () -> state.originY, v -> state.originY = v)
						.controller(opt -> IntegerFieldControllerBuilder.create(opt)
								.range(SchematicEntry.MIN_ORIGIN_Y, SchematicEntry.MAX_ORIGIN_Y))
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "remove"))
						.description(OptionDescription.of(Component.translatable(KEY + "remove.desc")))
						.binding(false, () -> state.remove, v -> state.remove = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.build();
	}
}
