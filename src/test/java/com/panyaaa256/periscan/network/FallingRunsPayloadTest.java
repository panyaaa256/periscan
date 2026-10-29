package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.network.FallingRunsPayload.ChunkRuns;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FallingRunsPayloadTest {
	@Test
	void everyValueSurvivesTheRoundTrip() {
		List<ChunkRuns> chunks = List.of(
				new ChunkRuns(-3, 7, new long[]{BlockPos.asLong(-48, 10, 112)}, new long[0]),
				// Emptied chunk: the client drops what it had there.
				new ChunkRuns(4, -1, new long[0], new long[0]),
				new ChunkRuns(0, 0, new long[0], new long[]{BlockPos.asLong(1, -59, 2), BlockPos.asLong(1, -59, 3)}));
		FallingRunsPayload payload = new FallingRunsPayload(3, chunks);

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		FallingRunsPayload.CODEC.encode(buf, payload);
		FallingRunsPayload decoded = FallingRunsPayload.CODEC.decode(buf);

		assertEquals(0, buf.readableBytes(), "bytes left after decoding");
		assertEquals(3, decoded.scanId());
		assertEquals(chunks.size(), decoded.chunks().size());
		for (int i = 0; i < chunks.size(); i++) {
			ChunkRuns expected = chunks.get(i);
			ChunkRuns actual = decoded.chunks().get(i);
			assertEquals(expected.chunkX(), actual.chunkX());
			assertEquals(expected.chunkZ(), actual.chunkZ());
			assertArrayEquals(expected.alongX(), actual.alongX());
			assertArrayEquals(expected.alongZ(), actual.alongZ());
		}
	}
}
