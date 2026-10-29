package com.panyaaa256.periscan;

import com.panyaaa256.periscan.network.PeriPayloads;
import com.panyaaa256.periscan.server.ServerScanManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs on both sides: on a dedicated server, and on the client for the
 * payload types and the integrated server of singleplayer games.
 */
public class PeriScan implements ModInitializer {
	public static final String MOD_ID = "periscan";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Payload types are registered before the handlers that receive them.
		PeriPayloads.register();
		ServerScanManager.init();
	}

	/** An identifier in this mod's namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
