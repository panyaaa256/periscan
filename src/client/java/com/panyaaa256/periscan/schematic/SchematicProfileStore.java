package com.panyaaa256.periscan.schematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.persist.SafeFiles;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Persists schematic profiles, shared by all worlds: one folder per profile
 * holding copies of its schematic files and a profile.json with where each is
 * placed. The files are copies, so moving or deleting the originals does not
 * break a profile. Every method takes the root folder, so tests can use their
 * own.
 */
public final class SchematicProfileStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	// Version of the file format, written as "version". Bump it when the format changes.
	static final int FORMAT_VERSION = 1;
	static final String PROFILE_FILE = "profile.json";

	/**
	 * Outcome of {@link #update}: the profile now saved, and the import sources
	 * that could not be copied.
	 */
	public record UpdateResult(SchematicProfile profile, List<Path> failedImports) {
	}

	private SchematicProfileStore() {
	}

	/** The folder holding every schematic profile. */
	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve(PeriScanClient.MOD_ID).resolve("schematics");
	}

	/**
	 * Whether the name can be a profile folder. Command arguments allow dots,
	 * so "." and ".." have to be rejected here.
	 */
	public static boolean isValidName(String name) {
		return name.matches("[A-Za-z0-9_+-][A-Za-z0-9_.+-]*");
	}

	/** The folder of a profile, holding its schematic files. */
	public static Path dir(Path root, String name) {
		return root.resolve(name);
	}

	public static boolean exists(Path root, String name) {
		return isValidName(name) && Files.isRegularFile(dir(root, name).resolve(PROFILE_FILE));
	}

	/** The names of all profiles, sorted. */
	public static List<String> names(Path root) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> dirs = Files.list(root)) {
			return dirs.map(dir -> dir.getFileName().toString())
					.filter(name -> exists(root, name))
					.sorted()
					.toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * A profile, its entries in their saved order; {@link SchematicProfile#EMPTY}
	 * if it does not exist. Invalid entries (only possible through hand edits)
	 * are skipped. An unreadable file is moved aside to profile.json.broken.
	 */
	public static SchematicProfile load(Path root, String name) {
		if (!exists(root, name)) {
			return SchematicProfile.EMPTY;
		}
		Path file = dir(root, name).resolve(PROFILE_FILE);
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			List<SchematicEntry> entries = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			for (JsonElement element : json.getAsJsonArray("schematics")) {
				SchematicEntry entry = entryFromJson(element);
				if (entry != null && seen.add(entry.fileName())) {
					entries.add(entry);
				} else {
					PeriScanClient.LOGGER.warn("PeriScan: skipping invalid schematic entry in {}: {}", file, element);
				}
			}
			int defaultOriginY = json.has("defaultOriginY") ? json.get("defaultOriginY").getAsInt()
					: SchematicEntry.DEFAULT_ORIGIN_Y;
			return new SchematicProfile(defaultOriginY, entries);
		} catch (Exception e) {
			try {
				Path backup = SafeFiles.moveAsideAsBroken(file);
				PeriScanClient.LOGGER.warn("PeriScan: could not read {}, moved it to {}: {}", file, backup, e.toString());
			} catch (IOException moveFailure) {
				PeriScanClient.LOGGER.error("PeriScan: could not read {} and could not back it up: {}", file, e.toString());
			}
			return SchematicProfile.EMPTY;
		}
	}

	/**
	 * Saves a profile (creating it if needed), then imports the given files
	 * into it. The copies of entries that are no longer listed are deleted. An
	 * imported file replaces a same-named copy; if that name is still listed its
	 * entry is kept, otherwise it gets a {@link SchematicEntry#imported new one}
	 * at the end. Imports sharing a file name fail, all of them: the profile
	 * could hold only one, and which one would be a guess.
	 */
	public static UpdateResult update(Path root, String name, SchematicProfile profile, List<Path> imports) {
		Path dir = dir(root, name);
		List<SchematicEntry> saved = new ArrayList<>(profile.entries());
		Set<String> importNames = new HashSet<>();
		Set<String> ambiguous = new HashSet<>();
		for (Path source : imports) {
			if (source.getFileName() != null && !importNames.add(source.getFileName().toString())) {
				ambiguous.add(source.getFileName().toString());
			}
		}
		Set<String> kept = new HashSet<>();
		saved.forEach(entry -> kept.add(entry.fileName()));
		List<Path> failed = new ArrayList<>();
		try {
			Files.createDirectories(dir);
			for (SchematicEntry old : load(root, name).entries()) {
				if (!kept.contains(old.fileName())) {
					Files.deleteIfExists(dir.resolve(old.fileName()));
				}
			}
		} catch (IOException e) {
			PeriScanClient.LOGGER.warn("PeriScan: could not update schematic profile folder {}: {}", dir, e.toString());
		}
		for (Path source : imports) {
			String fileName = source.getFileName() == null ? "" : source.getFileName().toString();
			try {
				if (!SchematicEntry.isValidFileName(fileName) || !Files.isRegularFile(source)) {
					throw new IOException("not a " + SchematicEntry.EXTENSION + " file");
				}
				if (ambiguous.contains(fileName)) {
					throw new IOException("several imports are named " + fileName);
				}
				Files.copy(source, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
				if (kept.add(fileName)) {
					saved.add(SchematicEntry.imported(fileName, profile.defaultOriginY()));
				}
			} catch (IOException e) {
				PeriScanClient.LOGGER.warn("PeriScan: could not import {} into {}: {}", source, dir, e.toString());
				failed.add(source);
			}
		}
		SchematicProfile result = new SchematicProfile(profile.defaultOriginY(), saved);
		write(dir.resolve(PROFILE_FILE), result);
		return new UpdateResult(result, List.copyOf(failed));
	}

	/**
	 * Saves a copy of profile {@code from}, with its schematic files, as
	 * {@code to}. The caller must have checked that {@code to} is a valid name
	 * and not taken.
	 */
	public static UpdateResult copy(Path root, String from, String to) {
		SchematicProfile profile = load(root, from);
		// Importing a file whose entry is listed keeps that entry.
		List<Path> files = profile.entries().stream().map(entry -> dir(root, from).resolve(entry.fileName())).toList();
		return update(root, to, profile, files);
	}

	/** Deletes a profile with its schematic copies. Returns false if there is no such profile. */
	public static boolean remove(Path root, String name) {
		if (!exists(root, name)) {
			return false;
		}
		// Deepest paths first, so folders are empty when their turn comes.
		try (Stream<Path> paths = Files.walk(dir(root, name))) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		} catch (IOException e) {
			PeriScanClient.LOGGER.warn("PeriScan: could not delete schematic profile {}: {}", dir(root, name), e.toString());
		}
		return true;
	}

	/** The entry of a JSON element, or null if it is not a valid one. */
	private static SchematicEntry entryFromJson(JsonElement element) {
		try {
			JsonObject json = element.getAsJsonObject();
			String fileName = json.get("file").getAsString();
			if (!SchematicEntry.isValidFileName(fileName)) {
				return null;
			}
			Set<Corner> corners = EnumSet.noneOf(Corner.class);
			for (JsonElement label : json.getAsJsonArray("corners")) {
				Corner corner = Corner.byLabel(label.getAsString());
				if (corner != null) {
					corners.add(corner);
				}
			}
			int originY = json.has("originY") ? json.get("originY").getAsInt() : SchematicEntry.DEFAULT_ORIGIN_Y;
			boolean keepOrientation = json.has("keepOrientation") && json.get("keepOrientation").getAsBoolean();
			return new SchematicEntry(fileName, corners, originY, keepOrientation);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Writes the entries atomically (see {@link SafeFiles#writeAtomically}). */
	private static void write(Path file, SchematicProfile profile) {
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
		root.addProperty("defaultOriginY", profile.defaultOriginY());
		JsonArray schematics = new JsonArray();
		for (SchematicEntry entry : profile.entries()) {
			JsonObject json = new JsonObject();
			json.addProperty("file", entry.fileName());
			JsonArray corners = new JsonArray();
			entry.corners().forEach(corner -> corners.add(corner.label()));
			json.add("corners", corners);
			json.addProperty("originY", entry.originY());
			json.addProperty("keepOrientation", entry.keepOrientation());
			schematics.add(json);
		}
		root.add("schematics", schematics);

		try {
			SafeFiles.writeAtomically(file, GSON.toJson(root));
		} catch (IOException e) {
			PeriScanClient.LOGGER.warn("PeriScan: could not save schematic profile to {}: {}", file, e.toString());
		}
	}
}
