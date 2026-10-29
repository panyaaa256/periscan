package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.PeriScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: stop the player's scan (/peri scan clear). */
public record ScanStopPayload() implements CustomPacketPayload {
	public static final ScanStopPayload INSTANCE = new ScanStopPayload();

	public static final Type<ScanStopPayload> TYPE = new Type<>(PeriScan.id("scan_stop"));

	public static final StreamCodec<FriendlyByteBuf, ScanStopPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ScanStopPayload> type() {
		return TYPE;
	}
}
