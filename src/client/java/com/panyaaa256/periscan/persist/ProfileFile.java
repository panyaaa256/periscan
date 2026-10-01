package com.panyaaa256.periscan.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.periscan.PeriScanClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The on-disk format of one world's profiles: reading (including migration of
 * the pre-profile formats and recovery from broken files) and writing. Knows
 * nothing about which world is current; see {@link ProfileStore}.
 */
final class ProfileFile {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	// Version of the file format, written as "version". Files of 0.3.0 and
	// earlier have none and are version 1. Bump it when the format changes, so
	// older PeriScan versions leave newer files alone.
	static final int FORMAT_VERSION = 1;
	// Longest readable part of a world file name; the hash keeps it unique anyway.
	private static final int MAX_READABLE_NAME_LENGTH = 48;
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
		// False when the file on disk could not be read nor backed up (saving
		// would overwrite the user's profiles with this empty data), or was
		// written by a newer PeriScan (saving would drop what it added).
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
	 * The file name (without extension) for a world: a readable part plus a hash
	 * of the whole key. sanitize alone maps different names to the same file
	 * (every Japanese name of the same length becomes the same run of '_').
	 */
	static String fileName(String prefix, String key) {
		String readable = sanitize(key);
		if (readable.length() > MAX_READABLE_NAME_LENGTH) {
			readable = readable.substring(0, MAX_READABLE_NAME_LENGTH);
		}
		return prefix + readable + "-" + shortHash(key);
	}

	/** File name (without extension) used up to 0.3.0, where names that sanitize alike shared a file. */
	static String legacyFileName(String prefix, String key) {
		return prefix + sanitize(key);
	}

	private static String shortHash(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 4);
		} catch (NoSuchAlgorithmException e) {
			// Every Java platform must provide SHA-256.
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Reads the file; when it does not exist yet, copies the data of the file
	 * the world used up to 0.3.0 into it (the old file is kept, as other worlds
	 * whose names collided may still need it).
	 */
	static Data read(Path file, Path legacyFile, LocalDate today) {
		if (Files.exists(file) || !Files.exists(legacyFile)) {
			return read(file, today);
		}
		Data data = read(legacyFile, today);
		if (!data.profiles.isEmpty()) {
			write(file, data);
			PeriScanClient.LOGGER.info("PeriScan: copied the profiles of {} to {}", legacyFile, file);
		}
		return data;
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
				return fromJson(file, root);
			}
			Data data = migrateLegacy(root, today);
			write(file, data);
			return data;
		} catch (Exception e) {
			return recoverFromBrokenFile(file, e);
		}
	}

	/**
	 * Writes the data atomically (see {@link SafeFiles#writeAtomically}); does
	 * nothing for non-writable data. Failures are logged; persistence is best
	 * effort and highlighting keeps working.
	 */
	static void write(Path file, Data data) {
		if (!data.writable) {
			return;
		}
		try {
			SafeFiles.writeAtomically(file, GSON.toJson(toJson(data)));
		} catch (IOException e) {
			PeriScanClient.LOGGER.warn("PeriScan: could not save profiles to {}: {}", file, e.toString());
		}
	}

	private static Data fromJson(Path file, JsonObject root) {
		Data data = new Data();
		int version = root.has("version") ? root.get("version").getAsInt() : 1;
		if (version > FORMAT_VERSION) {
			data.writable = false;
			PeriScanClient.LOGGER.warn("PeriScan: {} was written by a newer PeriScan (format {}); "
					+ "profile changes will not be saved", file, version);
		}
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
			if (isValidProfile(entry.getValue())) {
				data.profiles.put(entry.getKey(), GSON.fromJson(entry.getValue(), SavedProfile.class));
			} else {
				// Only possible through hand edits; the rest of the file stays usable.
				PeriScanClient.LOGGER.warn("PeriScan: skipping invalid profile '{}': {}", entry.getKey(), entry.getValue());
			}
		}
		if (root.has("lastScanned") && !root.get("lastScanned").isJsonNull()) {
			data.lastScanned = root.get("lastScanned").getAsString();
		}
		return data;
	}

	/**
	 * A profile needs its four chunk coordinates and its dimension; the commands
	 * rely on them. createdAt is informational and may be missing.
	 */
	private static boolean isValidProfile(JsonElement element) {
		if (!element.isJsonObject()) {
			return false;
		}
		JsonObject profile = element.getAsJsonObject();
		for (String field : new String[] { "minX", "minZ", "maxX", "maxZ" }) {
			if (!isInt(profile.get(field))) {
				return false;
			}
		}
		JsonElement dimension = profile.get("dimension");
		return dimension != null && dimension.isJsonPrimitive() && dimension.getAsJsonPrimitive().isString();
	}

	private static boolean isInt(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return false;
		}
		double value = element.getAsDouble();
		return value == Math.floor(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
	}

	private static JsonObject toJson(Data data) {
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
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
		try {
			Path backup = SafeFiles.moveAsideAsBroken(file);
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
