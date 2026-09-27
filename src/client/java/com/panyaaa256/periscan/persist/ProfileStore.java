package com.panyaaa256.periscan.persist;

import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.persist.ProfileFile.Data;
import com.panyaaa256.periscan.persist.ProfileFile.SavedProfile;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists peri profiles per world/server, plus the name of the profile that
 * was scanned last, so both survive relogging. Only profiles are stored;
 * highlights are rebuilt by rescanning chunks as they load. The file format
 * lives in {@link ProfileFile}; this class picks the current world's file.
 */
public final class ProfileStore {
	// Data of the current world/server, so commands and tab completion don't
	// re-read the file on every call. Reloaded when the world file changes.
	private static Path cachedFile;
	private static Data cachedData;

	private ProfileStore() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			invalidate();
			Data data = load();
			if (data.hasLastScannedProfile()) {
				// The profile stays dormant: scanning only starts when the user
				// runs /peri scan reload in the profile's dimension.
				ScanManager.INSTANCE.showDormantNotice();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> invalidate());
	}

	private static void invalidate() {
		cachedFile = null;
		cachedData = null;
	}

	/** All profiles of the current world/server, in insertion order. */
	public static Map<String, PeriProfile> profiles() {
		Data data = load();
		Map<String, PeriProfile> profiles = new LinkedHashMap<>();
		data.profiles.forEach((name, p) -> profiles.put(name, p.toProfile(name)));
		return profiles;
	}

	/** The profile with the given name, or null if there is none. */
	public static PeriProfile get(String name) {
		SavedProfile profile = load().profiles.get(name);
		return profile == null ? null : profile.toProfile(name);
	}

	public static void put(PeriProfile profile) {
		Data data = load();
		data.profiles.put(profile.name(), SavedProfile.of(profile));
		save(data);
	}

	/** Removes the profile; the last-scanned marker is cleared if it pointed there. */
	public static boolean remove(String name) {
		Data data = load();
		if (!data.remove(name)) {
			return false;
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

	private static Data load() {
		Path file = currentFile();
		if (file == null) {
			return new Data();
		}
		if (!file.equals(cachedFile)) {
			cachedData = ProfileFile.read(file, LocalDate.now());
			cachedFile = file;
		}
		return cachedData;
	}

	private static void save(Data data) {
		Path file = currentFile();
		if (file != null) {
			ProfileFile.write(file, data);
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
			return "sp_" + ProfileFile.sanitize(client.getSingleplayerServer().getWorldData().getLevelName());
		}
		ServerData server = client.getCurrentServer();
		if (server != null) {
			return "mp_" + ProfileFile.sanitize(server.ip);
		}
		return null;
	}
}
