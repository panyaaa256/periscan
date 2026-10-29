package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.PeriScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: every chunk of the region has had its turn. Every chunk
 * not listed here has been sent as a ChunkResultPayload.
 *
 * @param scanId      the scan request (ScanStartPayload) this belongs to
 * @param ungenerated chunks (ChunkPos longs) not generated yet (or only partly), so not scanned
 * @param unreadable  chunks whose saved data could not be read, so not scanned
 */
public record ScanFinishedPayload(int scanId, long[] ungenerated, long[] unreadable) implements CustomPacketPayload {

	public static final Type<ScanFinishedPayload> TYPE = new Type<>(PeriScan.id("scan_finished"));

	public static final StreamCodec<FriendlyByteBuf, ScanFinishedPayload> CODEC =
			StreamCodec.ofMember(ScanFinishedPayload::write, ScanFinishedPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(scanId);
		buf.writeLongArray(ungenerated);
		buf.writeLongArray(unreadable);
	}

	private static ScanFinishedPayload read(FriendlyByteBuf buf) {
		return new ScanFinishedPayload(buf.readVarInt(), buf.readLongArray(), buf.readLongArray());
	}

	@Override
	public Type<ScanFinishedPayload> type() {
		return TYPE;
	}
}
