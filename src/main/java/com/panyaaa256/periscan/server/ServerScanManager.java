package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.network.ScanStartPayload;
import com.panyaaa256.periscan.network.ScanStopPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The scans players asked the server for, at most one per player. A scan ends
 * when its player stops it, starts another one or leaves; a player coming back
 * starts it again with /peri scan reload.
 */
public final class ServerScanManager {
	// Only touched on the server thread: payload handlers and connection events run there.
	private static final Map<UUID, ScanStartPayload> REQUESTS = new HashMap<>();

	private ServerScanManager() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ScanStartPayload.TYPE,
				(payload, context) -> start(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ScanStopPayload.TYPE,
				(payload, context) -> stop(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.getPlayer()));
		// Singleplayer starts a new integrated server for every world it opens.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> REQUESTS.clear());
	}

	private static void start(ServerPlayer player, ScanStartPayload request) {
		REQUESTS.put(player.getUUID(), request);
		PeriScan.LOGGER.debug("PeriScan: {} started a scan of chunks {} to {} in {}", player.getName().getString(),
				request.cornerA(), request.cornerB(), request.dimension());
	}

	private static void stop(ServerPlayer player) {
		if (REQUESTS.remove(player.getUUID()) != null) {
			PeriScan.LOGGER.debug("PeriScan: {} stopped their scan", player.getName().getString());
		}
	}
}
