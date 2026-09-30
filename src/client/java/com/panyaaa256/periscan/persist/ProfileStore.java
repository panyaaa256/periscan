package com.panyaaa256.periscan.persist;

import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.persist.ProfileFile.Data;
import com.panyaaa256.periscan.persist.ProfileFile.SavedProfile;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

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
	/** The file of a world/server, and the one it used up to 0.3.0 (read once for migration). */
	private record WorldFiles(Path file, Path legacyFile) {
	}

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
		WorldFiles files = currentFiles();
		if (files == null) {
			return new Data();
		}
		if (!files.file().equals(cachedFile)) {
			cachedData = ProfileFile.read(files.file(), files.legacyFile(), LocalDate.now());
			cachedFile = files.file();
		}
		return cachedData;
	}

	private static void save(Data data) {
		WorldFiles files = currentFiles();
		if (files != null) {
			ProfileFile.write(files.file(), data);
		}
	}

	/**
	 * The files of the current world: singleplayer worlds are keyed by their
	 * save folder (the display name can be shared and renamed), servers by
	 * their address.
	 */
	private static WorldFiles currentFiles() {
		Minecraft client = Minecraft.getInstance();
		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			Path folder = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize().getFileName();
			String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
			return worldFiles(ProfileFile.fileName("sp_", folder.toString()), ProfileFile.legacyFileName("sp_", levelName));
		}
		ServerData server = client.getCurrentServer();
		if (server != null) {
			return worldFiles(ProfileFile.fileName("mp_", server.ip), ProfileFile.legacyFileName("mp_", server.ip));
		}
		return null;
	}

	private static WorldFiles worldFiles(String fileName, String legacyFileName) {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(PeriScanClient.MOD_ID).resolve("worlds");
		return new WorldFiles(dir.resolve(fileName + ".json"), dir.resolve(legacyFileName + ".json"));
	}
}
