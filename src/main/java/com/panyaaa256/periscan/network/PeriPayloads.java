package com.panyaaa256.periscan.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the payload types; both sides must, before handling or sending them. */
public final class PeriPayloads {
	// Upper bound of a scan request, which carries the client's block lists. The
	// payload is split into vanilla-sized packets, so long lists still fit.
	private static final int MAX_SCAN_START_SIZE = 1 << 20;

	private PeriPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(ScanStartPayload.TYPE, ScanStartPayload.CODEC, MAX_SCAN_START_SIZE);
		PayloadTypeRegistry.serverboundPlay().register(ScanStopPayload.TYPE, ScanStopPayload.CODEC);
	}
}
