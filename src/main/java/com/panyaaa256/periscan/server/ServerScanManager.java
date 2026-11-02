package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.network.ScanStartPayload;
import com.panyaaa256.periscan.network.ScanStopPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The scans players asked the server for, at most one per player. A scan ends
 * when its player stops it, starts another one or leaves; a player coming back
 * starts it again with /peri scan reload.
 */
public final class ServerScanManager {
	// Share of the time left until the next tick that scanning may use, shared
	// by all sessions. The rest stays for what the server does while it waits
	// (packets, chunk loading for the players).
	private static final double SCAN_BUDGET_SHARE = 0.6;
	// Time scanning may use when there is none left (an overloaded server, or
	// /tick sprint), so a scan still gets through.
	private static final long MIN_SCAN_BUDGET_NANOS = 1_000_000;
	// Ticks summed up in each timing line of the debug log.
	private static final int TIMING_LOG_TICKS = 20;

	// Only touched on the server thread: payload handlers, ticks and connection events run there.
	private static final Map<UUID, ServerScanSession> SESSIONS = new LinkedHashMap<>();

	// Scan timing since the last debug log line (see logTiming).
	private static int timingTicks;
	private static long timingBudgetNanos;
	private static long timingUsedNanos;
	private static int timingChunks;

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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			resetTiming();
		});
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

	/**
	 * Called for every block change of the server's loaded chunks (see
	 * LevelChunkMixin). Changes made off the server thread (by other mods) are
	 * not seen, as the sessions are only touched there.
	 */
	public static void onBlockChanged(ServerLevel level, BlockPos pos) {
		if (SESSIONS.isEmpty() || !level.getServer().isSameThread()) {
			return;
		}
		for (ServerScanSession session : SESSIONS.values()) {
			session.onBlockChanged(level.dimension(), pos);
		}
	}

	private static void onTick(MinecraftServer server) {
		// Before scanning: a chunk scanned below already shows this tick's changes.
		List<ServerScanSession> scanning = new ArrayList<>();
		for (ServerScanSession session : SESSIONS.values()) {
			session.sendBlockUpdates(server);
			if (!session.done()) {
				scanning.add(session);
			}
		}
		if (scanning.isEmpty()) {
			return;
		}
		// The server has already moved its next tick time on when this event runs
		// (after the tick time is measured), and it compares it with Util.getNanos(),
		// which is System.nanoTime() unless something replaces it.
		long start = System.nanoTime();
		long budget = Math.max(MIN_SCAN_BUDGET_NANOS,
				(long) ((server.getNextTickTime() - start) * SCAN_BUDGET_SHARE));
		// Each session gets an even share of what is left of the budget, so one
		// large region does not keep the other players waiting.
		long end = start + budget;
		int left = scanning.size();
		int chunks = 0;
		for (ServerScanSession session : scanning) {
			long now = System.nanoTime();
			if (now >= end) {
				break;
			}
			chunks += session.scanSome(server, now + (end - now) / left--);
		}
		logTiming(budget, System.nanoTime() - start, chunks);
	}

	/** Sums up the scan timing of the ticks that scanned, and logs it every TIMING_LOG_TICKS of them. */
	private static void logTiming(long budget, long used, int chunks) {
		timingTicks++;
		timingBudgetNanos += budget;
		timingUsedNanos += used;
		timingChunks += chunks;
		if (timingTicks < TIMING_LOG_TICKS) {
			return;
		}
		if (PeriScan.LOGGER.isDebugEnabled()) {
			PeriScan.LOGGER.debug("PeriScan: last {} ticks: {} ms budget, {} ms used, {} chunks per tick on average",
					timingTicks,
					String.format(Locale.ROOT, "%.2f", timingBudgetNanos / 1e6 / timingTicks),
					String.format(Locale.ROOT, "%.2f", timingUsedNanos / 1e6 / timingTicks),
					String.format(Locale.ROOT, "%.1f", (double) timingChunks / timingTicks));
		}
		resetTiming();
	}

	private static void resetTiming() {
		timingTicks = 0;
		timingBudgetNanos = 0;
		timingUsedNanos = 0;
		timingChunks = 0;
	}
}
