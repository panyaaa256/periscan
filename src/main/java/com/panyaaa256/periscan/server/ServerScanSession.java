package com.panyaaa256.periscan.server;

import com.panyaaa256.periscan.compat.ChunkPosCompat;
import com.panyaaa256.periscan.network.ChunkResultPayload;
import com.panyaaa256.periscan.network.ScanStartPayload;
import com.panyaaa256.periscan.scan.ZoneScanner;
import com.panyaaa256.periscan.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.UUID;

/**
 * One player's scan on the server. The chunks of the region are scanned a few
 * at a time (see scanSome), and each chunk's highlights are sent to the player
 * as soon as it is scanned.
 */
final class ServerScanSession {
	private final UUID player;
	private final int scanId;
	private final ResourceKey<Level> dimension;
	private final ZoneScanner scanner;
	// Chunks (ChunkPos longs) of the region that have not had their turn yet.
	// Chunks the server has not loaded when their turn comes stay unscanned.
	private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

	ServerScanSession(UUID player, ScanStartPayload request) {
		this.player = player;
		this.scanId = request.scanId();
		this.dimension = request.dimension();
		// Problems with the entries are not reported from here: the client compiles
		// the same settings itself and shows them.
		this.scanner = new ZoneScanner(request.cornerA(), request.cornerB(), request.settings(), new ArrayList<>());
		for (ChunkPos chunk : scanner.layout().chunks()) {
			queue.enqueue(ChunkPosCompat.chunkKey(chunk));
		}
	}

	/** Whether every chunk of the region has had its turn. */
	boolean done() {
		return queue.isEmpty();
	}

	/**
	 * Scans chunks in queue order until the queue is empty or System.nanoTime()
	 * reaches the deadline. Does nothing while the player or the dimension is
	 * missing (the session ends when the player leaves).
	 */
	void scanSome(MinecraftServer server, long deadline) {
		ServerPlayer target = server.getPlayerList().getPlayer(player);
		ServerLevel level = server.getLevel(dimension);
		if (target == null || level == null) {
			return;
		}
		int minY = ZoneScanner.minY(level);
		int maxY = scanner.maxY(level);
		while (!queue.isEmpty() && System.nanoTime() < deadline) {
			long key = queue.dequeueLong();
			int chunkX = ChunkPos.getX(key);
			int chunkZ = ChunkPos.getZ(key);
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk == null) {
				continue;
			}
			LongArrayList[] found = new LongArrayList[Zone.VALUES.length];
			for (int i = 0; i < found.length; i++) {
				found[i] = new LongArrayList();
			}
			scanner.scanChunk(chunkX, chunkZ, chunk.getSections(), chunk.getMinSectionY(), minY, maxY,
					(zone, pos) -> found[zone.ordinal()].add(pos));
			long[][] positions = new long[found.length][];
			for (int i = 0; i < found.length; i++) {
				positions[i] = found[i].toLongArray();
			}
			ServerPlayNetworking.send(target, new ChunkResultPayload(scanId, chunkX, chunkZ, positions));
		}
	}
}
