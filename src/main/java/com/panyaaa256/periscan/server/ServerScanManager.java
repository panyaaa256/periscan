package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.network.ScanStartPayload;
import com.panyaaa256.periscan.network.ScanStopPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The scans players asked the server for, at most one per player. A scan ends
 * when its player stops it, starts another one or leaves; a player coming back
 * starts it again with /peri scan reload.
 */
public final class ServerScanManager {
	// Time each server tick may spend scanning chunks, shared by all sessions,
	// so a large region does not hold up the tick.
	private static final long SCAN_BUDGET_NANOS = 5_000_000;

	// Only touched on the server thread: payload handlers, ticks and connection events run there.
	private static final Map<UUID, ServerScanSession> SESSIONS = new LinkedHashMap<>();

	private ServerScanManager() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ScanStartPayload.TYPE,
				(payload, context) -> start(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ScanStopPayload.TYPE,
				(payload, context) -> stop(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(ServerScanManager::onTick);
		// Singleplayer starts a new integrated server for every world it opens.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	private static void start(ServerPlayer player, ScanStartPayload request) {
		SESSIONS.put(player.getUUID(), new ServerScanSession(player.getUUID(), request));
		PeriScan.LOGGER.debug("PeriScan: {} started a scan of chunks {} to {} in {}", player.getName().getString(),
				request.cornerA(), request.cornerB(), request.dimension());
	}

	private static void stop(ServerPlayer player) {
		if (SESSIONS.remove(player.getUUID()) != null) {
			PeriScan.LOGGER.debug("PeriScan: {} stopped their scan", player.getName().getString());
		}
	}

	private static void onTick(MinecraftServer server) {
		List<ServerScanSession> scanning = new ArrayList<>();
		for (ServerScanSession session : SESSIONS.values()) {
			if (!session.done()) {
				scanning.add(session);
			}
		}
		// Each session gets an even share of what is left of the budget, so one
		// large region does not keep the other players waiting.
		long end = System.nanoTime() + SCAN_BUDGET_NANOS;
		int left = scanning.size();
		for (ServerScanSession session : scanning) {
			long now = System.nanoTime();
			if (now >= end) {
				break;
			}
			session.scanSome(server, now + (end - now) / left--);
		}
	}
}
