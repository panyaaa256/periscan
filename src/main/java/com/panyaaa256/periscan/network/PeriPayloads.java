package com.panyaaa256.periscan.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the payload types; both sides must, before handling or sending them. */
public final class PeriPayloads {
	// Upper bound of a scan request, which carries the client's block lists. The
	// payload is split into vanilla-sized packets, so long lists still fit.
	private static final int MAX_SCAN_START_SIZE = 1 << 20;
	// Upper bound of one chunk's highlights. A zone whose list matches the
	// common blocks can highlight most of a chunk: 16x16 columns times the scan
	// height (up to about 2000 layers) times 8 bytes, for more than one zone.
	private static final int MAX_CHUNK_RESULT_SIZE = 16 << 20;
	// Every chunk of the largest region (512 x 512) listed once, 8 bytes each, with room to spare.
	private static final int MAX_SCAN_FINISHED_SIZE = 4 << 20;
	// Changed blocks of one tick, 9 bytes each.
	private static final int MAX_BLOCK_UPDATES_SIZE = 16 << 20;
	// Run highlights of the chunks changed in one tick, 8 bytes each.
	private static final int MAX_FALLING_RUNS_SIZE = 16 << 20;

	private PeriPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(ScanStartPayload.TYPE, ScanStartPayload.CODEC, MAX_SCAN_START_SIZE);
		PayloadTypeRegistry.serverboundPlay().register(ScanStopPayload.TYPE, ScanStopPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(ChunkResultPayload.TYPE, ChunkResultPayload.CODEC, MAX_CHUNK_RESULT_SIZE);
		// Lists chunks by the thousand when a large region is mostly ungenerated.
		PayloadTypeRegistry.clientboundPlay().registerLarge(ScanFinishedPayload.TYPE, ScanFinishedPayload.CODEC, MAX_SCAN_FINISHED_SIZE);
		// A world eater or a large explosion changes thousands of blocks in one tick.
		PayloadTypeRegistry.clientboundPlay().registerLarge(BlockUpdatesPayload.TYPE, BlockUpdatesPayload.CODEC, MAX_BLOCK_UPDATES_SIZE);
		// The first flush after a scan can cover every chunk of the trench strips.
		PayloadTypeRegistry.clientboundPlay().registerLarge(FallingRunsPayload.TYPE, FallingRunsPayload.CODEC, MAX_FALLING_RUNS_SIZE);
	}
}
