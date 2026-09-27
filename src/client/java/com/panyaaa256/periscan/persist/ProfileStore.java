package com.panyaaa256.periscan.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists peri profiles per world/server, plus the name of the profile that
 * was scanned last, so both survive relogging. Only profiles are stored;
 * highlights are rebuilt by rescanning chunks as they load.
 */
public final class ProfileStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record SavedProfile(int minX, int minZ, int maxX, int maxZ, String dimension, String createdAt) {
	}

	private record LegacyRegion(int x1, int z1, int x2, int z2) {
	}

	private static final class Data {
		LinkedHashMap<String, SavedProfile> profiles = new LinkedHashMap<>();
		String lastScanned;
	}

	private ProfileStore() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			Data data = load();
			if (data.lastScanned != null && data.profiles.containsKey(data.lastScanned)) {
				// The profile stays dormant: scanning only starts when the user
				// runs /peri scan reload in the profile's dimension.
				ScanManager.INSTANCE.showDormantNotice();
			}
		});
	}

	/** All profiles of the current world/server, in insertion order. */
	public static Map<String, PeriProfile> profiles() {
		Data data = load();
		Map<String, PeriProfile> profiles = new LinkedHashMap<>();
		data.profiles.forEach((name, p) -> profiles.put(name, toProfile(name, p)));
		return profiles;
	}

	/** The profile with the given name, or null if there is none. */
	public static PeriProfile get(String name) {
		SavedProfile profile = load().profiles.get(name);
		return profile == null ? null : toProfile(name, profile);
	}

	public static void put(PeriProfile profile) {
		Data data = load();
		data.profiles.put(profile.name(), new SavedProfile(profile.minX(), profile.minZ(),
				profile.maxX(), profile.maxZ(), profile.dimension(), profile.createdAt()));
		save(data);
	}

	/** Removes the profile; the last-scanned marker is cleared if it pointed there. */
	public static boolean remove(String name) {
		Data data = load();
		if (data.profiles.remove(name) == null) {
			return false;
		}
		if (name.equals(data.lastScanned)) {
			data.lastScanned = null;
		}
		save(data);
		return true;
	}

	/** Name of the profile scanned last, or null. May point to a removed profile. */
	public static String lastScanned() {
		return load().lastScanned;
	}

	public static void setLastScanned(String name) {
		Data data = load();
		data.lastScanned = name;
		save(data);
	}

	public static void clearLastScanned() {
		Data data = load();
		if (data.lastScanned == null) {
			return;
		}
		data.lastScanned = null;
		save(data);
	}

	private static PeriProfile toProfile(String name, SavedProfile p) {
		return new PeriProfile(name, p.minX, p.minZ, p.maxX, p.maxZ, p.dimension, p.createdAt);
	}

	private static Data load() {
		Path file = currentFile();
		if (file == null || !Files.exists(file)) {
			return new Data();
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (root.has("profiles")) {
				Data data = new Data();
				for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
					data.profiles.put(entry.getKey(), GSON.fromJson(entry.getValue(), SavedProfile.class));
				}
				if (root.has("lastScanned") && !root.get("lastScanned").isJsonNull()) {
					data.lastScanned = root.get("lastScanned").getAsString();
				}
				return data;
			}
			// Legacy pre-profile formats: convert once and write back.
			Data data = migrateLegacy(root);
			save(data);
			return data;
		} catch (Exception e) {
			return new Data();
		}
	}

	private static Data migrateLegacy(JsonObject root) {
		Data data = new Data();
		if (root.has("x1")) {
			// Oldest format: a single region, implicitly the overworld.
			addLegacy(data, Level.OVERWORLD.identifier().toString(), GSON.fromJson(root, LegacyRegion.class));
			return data;
		}
		// One region per dimension, keyed by dimension id.
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			addLegacy(data, entry.getKey(), GSON.fromJson(entry.getValue(), LegacyRegion.class));
		}
		return data;
	}

	private static void addLegacy(Data data, String dimension, LegacyRegion region) {
		// Name migrated profiles after the dimension path ("overworld", "the_nether").
		String name = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
		data.profiles.put(name, new SavedProfile(
				Math.min(region.x1, region.x2), Math.min(region.z1, region.z2),
				Math.max(region.x1, region.x2), Math.max(region.z1, region.z2),
				dimension, LocalDate.now().toString()));
		if (data.lastScanned == null) {
			data.lastScanned = name;
		}
	}

	private static void save(Data data) {
		Path file = currentFile();
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		JsonObject profiles = new JsonObject();
		data.profiles.forEach((name, p) -> profiles.add(name, GSON.toJsonTree(p)));
		root.add("profiles", profiles);
		if (data.lastScanned != null) {
			root.addProperty("lastScanned", data.lastScanned);
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root));
		} catch (IOException e) {
			// Persistence is best effort; highlighting itself keeps working.
		}
	}

	private static Path currentFile() {
		String key = worldKey();
		if (key == null) {
			return null;
		}
		return FabricLoader.getInstance().getConfigDir().resolve(PeriScanClient.MOD_ID).resolve("worlds").resolve(key + ".json");
	}

	private static String worldKey() {
		Minecraft client = Minecraft.getInstance();
		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			return "sp_" + sanitize(client.getSingleplayerServer().getWorldData().getLevelName());
		}
		ServerData server = client.getCurrentServer();
		if (server != null) {
			return "mp_" + sanitize(server.ip);
		}
		return null;
	}

	private static String sanitize(String name) {
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
