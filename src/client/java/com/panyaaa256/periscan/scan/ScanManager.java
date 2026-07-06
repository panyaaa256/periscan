package com.panyaaa256.periscan.scan;

import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.zone.Zone;
import com.panyaaa256.periscan.zone.ZoneLayout;
import com.panyaaa256.periscan.zone.ZoneMatcher;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class ScanManager {
	public static final ScanManager INSTANCE = new ScanManager();

	private ZoneLayout layout;
	private ChunkPos cornerA;
	private ChunkPos cornerB;
	private final EnumMap<Zone, ZoneMatcher> matchers = new EnumMap<>(Zone.class);
	private final EnumMap<Zone, LongOpenHashSet> highlights = new EnumMap<>(Zone.class);
	private final LongOpenHashSet pendingChunks = new LongOpenHashSet();
	private int tickCounter = 0;
	private boolean dormantNoticePending = false;

	private ScanManager() {
		for (Zone zone : Zone.VALUES) {
			highlights.put(zone, new LongOpenHashSet());
		}
	}

	public void init() {
		ClientChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> deactivate());
	}

	public boolean isActive() {
		return layout != null;
	}

	/** A region is known (active, or restored from disk and waiting for /periscan reload). */
	public boolean hasRegion() {
		return cornerA != null;
	}

	/**
	 * Remembers a region without scanning or highlighting anything. Used when
	 * restoring on login: scanning only starts once /periscan reload is run.
	 */
	public void setDormantRegion(ChunkPos a, ChunkPos b) {
		deactivate();
		this.cornerA = a;
		this.cornerB = b;
		this.dormantNoticePending = true;
	}

	public ChunkPos cornerA() {
		return cornerA;
	}

	public ChunkPos cornerB() {
		return cornerB;
	}

	public LongOpenHashSet highlights(Zone zone) {
		return highlights.get(zone);
	}

	public int pendingChunkCount() {
		return pendingChunks.size();
	}

	/** Chunks in the region not scanned yet (as ChunkPos longs). Do not modify. */
	public LongOpenHashSet pendingChunks() {
		return pendingChunks;
	}

	/**
	 * Activates highlighting for the given perimeter (chunk coordinates, outermost
	 * rectangle). Returns config entries that could not be parsed, for feedback.
	 */
	public List<String> activate(ChunkPos a, ChunkPos b) {
		PeriScanConfig config = PeriScanConfig.get();
		this.cornerA = a;
		this.cornerB = b;
		this.layout = ZoneLayout.of(a, b, config);

		List<String> invalidEntries = new ArrayList<>();
		ZoneMatcher.WaterloggedExclusions exclusions = ZoneMatcher.WaterloggedExclusions.compile(
				config.waterloggedBlacklist, config.waterloggedExcludePushDestroy, invalidEntries);
		matchers.clear();
		for (Zone zone : Zone.VALUES) {
			if (zone == Zone.TRENCH_INNER) {
				// Walls/fences only matter in specific trench columns (index % 3 == 2 from the edge).
				matchers.put(zone, ZoneMatcher.compile(zone.blockEntries(config),
						config.trenchInnerFenceBlocks, layout::fenceLaneMatters,
						zone.includeWaterlogged(config), exclusions, invalidEntries));
			} else {
				matchers.put(zone, ZoneMatcher.compile(zone.blockEntries(config),
						zone.includeWaterlogged(config), exclusions, invalidEntries));
			}
		}

		for (LongOpenHashSet set : highlights.values()) {
			set.clear();
		}
		pendingChunks.clear();
		for (ChunkPos chunk : layout.chunks()) {
			pendingChunks.add(chunk.toLong());
		}

		// Scan whatever is already loaded; the rest is picked up by CHUNK_LOAD.
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			LongIterator it = pendingChunks.iterator();
			List<LevelChunk> loaded = new ArrayList<>();
			while (it.hasNext()) {
				long key = it.nextLong();
				LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
				if (chunk != null) {
					loaded.add(chunk);
					it.remove();
				}
			}
			for (LevelChunk chunk : loaded) {
				scanChunk(level, chunk);
			}
		}
		return invalidEntries;
	}

	/** Re-derives zones/matchers from current config and rescans, keeping the region. */
	public List<String> rescan() {
		if (!isActive()) {
			return List.of();
		}
		return activate(cornerA, cornerB);
	}

	public void deactivate() {
		layout = null;
		cornerA = null;
		cornerB = null;
		dormantNoticePending = false;
		matchers.clear();
		pendingChunks.clear();
		for (LongOpenHashSet set : highlights.values()) {
			set.clear();
		}
	}

	private void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (layout == null || !layout.intersectsChunk(chunk.getPos())) {
			return;
		}
		pendingChunks.remove(chunk.getPos().toLong());
		// Remove stale highlights from this chunk before rescanning it (covers
		// changes that happened while the chunk was unloaded).
		clearChunkHighlights(chunk.getPos());
		scanChunk(level, chunk);
	}

	private void clearChunkHighlights(ChunkPos pos) {
		for (LongOpenHashSet set : highlights.values()) {
			set.removeIf(key -> (BlockPos.getX(key) >> 4) == pos.x && (BlockPos.getZ(key) >> 4) == pos.z);
		}
	}

	private void scanChunk(ClientLevel level, LevelChunk chunk) {
		ChunkPos cp = chunk.getPos();
		int baseX = cp.getMinBlockX();
		int baseZ = cp.getMinBlockZ();

		byte[] columnMasks = new byte[256];
		boolean anyColumn = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int mask = layout.zoneMask(baseX + dx, baseZ + dz);
				columnMasks[(dx << 4) | dz] = (byte) mask;
				anyColumn |= mask != 0;
			}
		}
		if (!anyColumn) {
			return;
		}

		int minY = Math.max(level.getMinY(), scanMinY(level));
		int maxY = Math.min(level.getMaxY(), PeriScanConfig.get().scanMaxY);
		for (int y = minY; y <= maxY; y++) {
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
			if (section.hasOnlyAir()) {
				// Jump to the end of this section.
				y = (((y >> 4) + 1) << 4) - 1;
				continue;
			}
			int localY = y & 15;
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					int mask = columnMasks[(dx << 4) | dz];
					if (mask == 0) {
						continue;
					}
					BlockState state = section.getBlockState(dx, localY, dz);
					if (state.isAir()) {
						continue;
					}
					for (Zone zone : Zone.VALUES) {
						if ((mask & zone.mask()) != 0 && matchers.get(zone).matches(state, baseX + dx, baseZ + dz)) {
							highlights.get(zone).add(BlockPos.asLong(baseX + dx, y, baseZ + dz));
						}
					}
				}
			}
		}
	}

	/** Skips the bedrock floor: overworld scans only y > -59, the nether only y > 5. */
	public static int scanMinY(ClientLevel level) {
		if (level.dimension() == Level.OVERWORLD) {
			return -59;
		}
		if (level.dimension() == Level.NETHER) {
			return 5;
		}
		return level.getMinY();
	}

	private void onTick(Minecraft client) {
		if (client.level == null) {
			return;
		}
		if (dormantNoticePending && client.player != null) {
			dormantNoticePending = false;
			client.player.displayClientMessage(Component.translatable("periscan.msg.region_available"), false);
		}
		if (layout == null) {
			return;
		}
		tickCounter++;

		// Drop highlights whose block has been replaced (by anyone) with something
		// that no longer matches. Block updates reach the client, so re-checking the
		// current state covers other players' changes too.
		if (tickCounter % 10 == 0) {
			validateHighlights(client.level);
		}

		if (!pendingChunks.isEmpty() && tickCounter % 20 == 0 && client.player != null) {
			client.player.displayClientMessage(
					Component.translatable("periscan.msg.pending_chunks", pendingChunks.size()), true);
		}
	}

	private void validateHighlights(ClientLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Zone zone : Zone.VALUES) {
			ZoneMatcher matcher = matchers.get(zone);
			if (matcher == null) {
				continue;
			}
			highlights.get(zone).removeIf(key -> {
				pos.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
				if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					return false; // keep cached highlights for unloaded chunks
				}
				return !matcher.matches(level.getBlockState(pos), pos.getX(), pos.getZ());
			});
		}
	}
}
