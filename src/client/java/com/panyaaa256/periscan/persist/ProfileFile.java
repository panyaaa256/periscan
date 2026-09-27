package com.panyaaa256.periscan.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.periscan.PeriScanClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The on-disk format of one world's profiles: reading (including migration of
 * the pre-profile formats and recovery from broken files) and writing. Knows
 * nothing about which world is current; see {@link ProfileStore}.
 */
final class ProfileFile {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	// Legacy single-region files never stored a dimension: they were overworld only.
	private static final String LEGACY_DIMENSION = "minecraft:overworld";

	record SavedProfile(int minX, int minZ, int maxX, int maxZ, String dimension, String createdAt) {

		static SavedProfile of(PeriProfile profile) {
			return new SavedProfile(profile.minX(), profile.minZ(), profile.maxX(), profile.maxZ(),
					profile.dimension(), profile.createdAt());
		}

		PeriProfile toProfile(String name) {
			return new PeriProfile(name, minX, minZ, maxX, maxZ, dimension, createdAt);
		}
	}

	private record LegacyRegion(int x1, int z1, int x2, int z2) {
	}

	/** One world's profiles (in insertion order) and the last-scanned marker. */
	static final class Data {
		final LinkedHashMap<String, SavedProfile> profiles = new LinkedHashMap<>();
		String lastScanned;
		// False when the file on disk could not be read nor backed up: saving
		// would overwrite the user's profiles with this (empty) data.
		boolean writable = true;

		/** Removes the profile; the last-scanned marker is cleared if it pointed there. */
		boolean remove(String name) {
			if (profiles.remove(name) == null) {
				return false;
			}
			if (name.equals(lastScanned)) {
				lastScanned = null;
			}
			return true;
		}

		/** Whether the last-scanned profile still exists (so it can be offered for reload). */
		boolean hasLastScannedProfile() {
			return lastScanned != null && profiles.containsKey(lastScanned);
		}
	}

	private ProfileFile() {
	}

	/** Characters allowed in world file names; everything else becomes '_'. */
	static String sanitize(String name) {
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	/**
	 * Reads the file; a missing file is empty data. Legacy formats are converted
	 * and written back (migrated profiles are dated {@code today}). An unreadable
	 * file is moved aside to {@code <name>.broken} so the next save does not
	 * overwrite it; if even that fails, the returned data is not writable.
	 */
	static Data read(Path file, LocalDate today) {
		if (!Files.exists(file)) {
			return new Data();
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (root.has("profiles")) {
				return fromJson(root);
			}
			Data data = migrateLegacy(root, today);
			write(file, data);
			return data;
		} catch (Exception e) {
			return recoverFromBrokenFile(file, e);
		}
	}

	/** Writes the data; does nothing for non-writable data. Failures are ignored (best effort). */
	static void write(Path file, Data data) {
		if (!data.writable) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(toJson(data)));
		} catch (IOException e) {
			// Persistence is best effort; highlighting itself keeps working.
		}
	}

	private static Data fromJson(JsonObject root) {
		Data data = new Data();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
			data.profiles.put(entry.getKey(), GSON.fromJson(entry.getValue(), SavedProfile.class));
		}
		if (root.has("lastScanned") && !root.get("lastScanned").isJsonNull()) {
			data.lastScanned = root.get("lastScanned").getAsString();
		}
		return data;
	}

	private static JsonObject toJson(Data data) {
		JsonObject root = new JsonObject();
		JsonObject profiles = new JsonObject();
		data.profiles.forEach((name, p) -> profiles.add(name, GSON.toJsonTree(p)));
		root.add("profiles", profiles);
		if (data.lastScanned != null) {
			root.addProperty("lastScanned", data.lastScanned);
		}
		return root;
	}

	private static Data recoverFromBrokenFile(Path file, Exception cause) {
		Data data = new Data();
		Path backup = file.resolveSibling(file.getFileName() + ".broken");
		try {
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			PeriScanClient.LOGGER.warn("PeriScan: could not read {}, moved it to {}: {}", file, backup, cause.toString());
		} catch (IOException e) {
			data.writable = false;
			PeriScanClient.LOGGER.error("PeriScan: could not read {} and could not back it up; "
					+ "profile changes will not be saved: {}", file, cause.toString());
		}
		return data;
	}

	private static Data migrateLegacy(JsonObject root, LocalDate today) {
		Data data = new Data();
		if (root.has("x1")) {
			// Oldest format: a single region, implicitly the overworld.
			addLegacy(data, LEGACY_DIMENSION, GSON.fromJson(root, LegacyRegion.class), today);
			return data;
		}
		// One region per dimension, keyed by dimension id.
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			addLegacy(data, entry.getKey(), GSON.fromJson(entry.getValue(), LegacyRegion.class), today);
		}
		return data;
	}

	private static void addLegacy(Data data, String dimension, LegacyRegion region, LocalDate today) {
		// Name migrated profiles after the dimension path ("overworld", "the_nether").
		String name = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
		data.profiles.put(name, new SavedProfile(
				Math.min(region.x1, region.x2), Math.min(region.z1, region.z2),
				Math.max(region.x1, region.x2), Math.max(region.z1, region.z2),
				dimension, today.toString()));
		if (data.lastScanned == null) {
			data.lastScanned = name;
		}
	}
}
