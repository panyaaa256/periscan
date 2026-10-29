package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.PeriScan;
import com.panyaaa256.periscan.zone.Zone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the highlights of one scanned chunk, replacing what the
 * client had for it. Sent for every scanned chunk, even one without
 * highlights, so the client knows it is scanned.
 *
 * @param scanId    the scan request (ScanStartPayload) this result belongs to
 * @param positions the highlighted blocks (BlockPos longs) of each zone, indexed by Zone.ordinal()
 */
public record ChunkResultPayload(int scanId, int chunkX, int chunkZ, long[][] positions)
		implements CustomPacketPayload {

	public static final Type<ChunkResultPayload> TYPE = new Type<>(PeriScan.id("chunk_result"));

	public static final StreamCodec<FriendlyByteBuf, ChunkResultPayload> CODEC =
			StreamCodec.ofMember(ChunkResultPayload::write, ChunkResultPayload::read);

	public ChunkResultPayload {
		if (positions.length != Zone.VALUES.length) {
			throw new IllegalArgumentException("one position array per zone expected, got " + positions.length);
		}
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(scanId);
		buf.writeInt(chunkX);
		buf.writeInt(chunkZ);
		for (long[] zonePositions : positions) {
			buf.writeLongArray(zonePositions);
		}
	}

	private static ChunkResultPayload read(FriendlyByteBuf buf) {
		int scanId = buf.readVarInt();
		int chunkX = buf.readInt();
		int chunkZ = buf.readInt();
		long[][] positions = new long[Zone.VALUES.length][];
		for (int i = 0; i < positions.length; i++) {
			positions[i] = buf.readLongArray();
		}
		return new ChunkResultPayload(scanId, chunkX, chunkZ, positions);
	}

	@Override
	public Type<ChunkResultPayload> type() {
		return TYPE;
	}
}
