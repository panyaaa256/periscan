package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.PeriScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: blocks of scanned chunks that changed since the last tick,
 * each with the zones that now highlight it (Zone.mask() bits, 0 for none).
 * The client replaces whatever it had for those positions.
 *
 * @param scanId    the scan request (ScanStartPayload) this belongs to
 * @param positions the changed blocks (BlockPos longs)
 * @param zoneMasks the zones of each position, at the same index
 */
public record BlockUpdatesPayload(int scanId, long[] positions, byte[] zoneMasks) implements CustomPacketPayload {

	public static final Type<BlockUpdatesPayload> TYPE = new Type<>(PeriScan.id("block_updates"));

	public static final StreamCodec<FriendlyByteBuf, BlockUpdatesPayload> CODEC =
			StreamCodec.ofMember(BlockUpdatesPayload::write, BlockUpdatesPayload::read);

	public BlockUpdatesPayload {
		if (positions.length != zoneMasks.length) {
			throw new IllegalArgumentException("one zone mask per position expected: "
					+ positions.length + " positions, " + zoneMasks.length + " masks");
		}
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(scanId);
		buf.writeLongArray(positions);
		buf.writeByteArray(zoneMasks);
	}

	private static BlockUpdatesPayload read(FriendlyByteBuf buf) {
		return new BlockUpdatesPayload(buf.readVarInt(), buf.readLongArray(), buf.readByteArray());
	}

	@Override
	public Type<BlockUpdatesPayload> type() {
		return TYPE;
	}
}
