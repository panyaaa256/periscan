package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.PeriScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: the falling-block run highlights of the chunks whose runs
 * changed. The client replaces what it had for each listed chunk.
 *
 * @param scanId the scan request (ScanStartPayload) this belongs to
 */
public record FallingRunsPayload(int scanId, List<ChunkRuns> chunks) implements CustomPacketPayload {

	/** The highlighted blocks (BlockPos longs) of one chunk, by run direction. */
	public record ChunkRuns(int chunkX, int chunkZ, long[] alongX, long[] alongZ) {
	}

	public static final Type<FallingRunsPayload> TYPE = new Type<>(PeriScan.id("falling_runs"));

	public static final StreamCodec<FriendlyByteBuf, FallingRunsPayload> CODEC =
			StreamCodec.ofMember(FallingRunsPayload::write, FallingRunsPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(scanId);
		buf.writeVarInt(chunks.size());
		for (ChunkRuns chunk : chunks) {
			buf.writeInt(chunk.chunkX());
			buf.writeInt(chunk.chunkZ());
			buf.writeLongArray(chunk.alongX());
			buf.writeLongArray(chunk.alongZ());
		}
	}

	private static FallingRunsPayload read(FriendlyByteBuf buf) {
		int scanId = buf.readVarInt();
		int count = buf.readVarInt();
		List<ChunkRuns> chunks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			chunks.add(new ChunkRuns(buf.readInt(), buf.readInt(), buf.readLongArray(), buf.readLongArray()));
		}
		return new FallingRunsPayload(scanId, chunks);
	}

	@Override
	public Type<FallingRunsPayload> type() {
		return TYPE;
	}
}
