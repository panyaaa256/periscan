package com.panyaaa256.periscan.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the active perimeter region per world/server, so it survives
 * relogging. Only the region (chunk coordinates) is stored; highlights are
 * rebuilt by rescanning chunks as they load.
 */
public final class RegionStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record SavedRegion(int x1, int z1, int x2, int z2) {
	}

	private RegionStore() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			SavedRegion region = load();
			if (region != null) {
				// Restore the region but stay dormant: scanning only starts when the
				// user runs /periscan reload.
				ScanManager.INSTANCE.setDormantRegion(new ChunkPos(region.x1, region.z1), new ChunkPos(region.x2, region.z2));
			}
		});
	}

	public static void save(ChunkPos a, ChunkPos b) {
		Path file = currentFile();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(new SavedRegion(a.x, a.z, b.x, b.z)));
		} catch (IOException e) {
			// Persistence is best effort; highlighting itself keeps working.
		}
	}

	public static void delete() {
		Path file = currentFile();
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
		}
	}

	private static SavedRegion load() {
		Path file = currentFile();
		if (file == null || !Files.exists(file)) {
			return null;
		}
		try {
			return GSON.fromJson(Files.readString(file), SavedRegion.class);
		} catch (Exception e) {
			return null;
		}
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
