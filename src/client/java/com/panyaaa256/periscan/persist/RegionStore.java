package com.panyaaa256.periscan.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists perimeter regions per world/server and per dimension, so they
 * survive relogging. Only the region (chunk coordinates) is stored; highlights
 * are rebuilt by rescanning chunks as they load.
 */
public final class RegionStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record SavedRegion(int x1, int z1, int x2, int z2) {
	}

	/** A saved region as its two corner chunks. */
	public record Region(ChunkPos a, ChunkPos b) {
	}

	private RegionStore() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!loadAll().isEmpty()) {
				// Regions stay dormant: scanning only starts when the user runs
				// /periscan reload in the region's dimension.
				ScanManager.INSTANCE.showDormantNotice();
			}
		});
	}

	public static void save(ResourceKey<Level> dimension, ChunkPos a, ChunkPos b) {
		Path file = currentFile();
		if (file == null) {
			return;
		}
		Map<String, SavedRegion> regions = loadAll();
		regions.put(dimension.identifier().toString(), new SavedRegion(a.x, a.z, b.x, b.z));
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(regions));
		} catch (IOException e) {
			// Persistence is best effort; highlighting itself keeps working.
		}
	}

	public static void delete(ResourceKey<Level> dimension) {
		Path file = currentFile();
		if (file == null) {
			return;
		}
		Map<String, SavedRegion> regions = loadAll();
		if (regions.remove(dimension.identifier().toString()) == null) {
			return;
		}
		try {
			if (regions.isEmpty()) {
				Files.deleteIfExists(file);
			} else {
				Files.writeString(file, GSON.toJson(regions));
			}
		} catch (IOException ignored) {
		}
	}

	/** The saved region of the given dimension, or null if there is none. */
	public static Region load(ResourceKey<Level> dimension) {
		SavedRegion region = loadAll().get(dimension.identifier().toString());
		return region == null ? null
				: new Region(new ChunkPos(region.x1, region.z1), new ChunkPos(region.x2, region.z2));
	}

	private static Map<String, SavedRegion> loadAll() {
		Map<String, SavedRegion> regions = new LinkedHashMap<>();
		Path file = currentFile();
		if (file == null || !Files.exists(file)) {
			return regions;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (root.has("x1")) {
				// Legacy single-region format from before regions were per-dimension.
				regions.put(Level.OVERWORLD.identifier().toString(), GSON.fromJson(root, SavedRegion.class));
				return regions;
			}
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				regions.put(entry.getKey(), GSON.fromJson(entry.getValue(), SavedRegion.class));
			}
		} catch (Exception e) {
			regions.clear();
		}
		return regions;
	}

	private static Path currentFile() {
		String key = worldKey();
		if (key == null) {
			return null;
		}
		return FabricLoader.getInstance().getConfigDir().resolve("periscan").resolve("worlds").resolve(key + ".json");
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
