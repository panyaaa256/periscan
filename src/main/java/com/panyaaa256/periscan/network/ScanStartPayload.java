package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.scan.ScanSettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Client to server: scan the given perimeter (chunk coordinates, outermost
 * rectangle, as in a profile) in the given dimension with the client's settings.
 * Replaces the player's previous scan, if any.
 */
public record ScanStartPayload(ResourceKey<Level> dimension, ChunkPos cornerA, ChunkPos cornerB,
		ScanSettings settings) implements CustomPacketPayload {

	public static final Type<ScanStartPayload> TYPE = new Type<>(PeriScan.id("scan_start"));

	public static final StreamCodec<FriendlyByteBuf, ScanStartPayload> CODEC =
			StreamCodec.ofMember(ScanStartPayload::write, ScanStartPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeResourceKey(dimension);
		buf.writeChunkPos(cornerA);
		buf.writeChunkPos(cornerB);
		ScanSettings.STREAM_CODEC.encode(buf, settings);
	}

	private static ScanStartPayload read(FriendlyByteBuf buf) {
		return new ScanStartPayload(buf.readResourceKey(Registries.DIMENSION), buf.readChunkPos(), buf.readChunkPos(),
				ScanSettings.STREAM_CODEC.decode(buf));
	}

	@Override
	public Type<ScanStartPayload> type() {
		return TYPE;
	}
}
