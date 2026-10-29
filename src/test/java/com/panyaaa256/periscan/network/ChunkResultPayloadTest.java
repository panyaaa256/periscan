package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.zone.Zone;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkResultPayloadTest {
	@Test
	void everyValueSurvivesTheRoundTrip() {
		long[][] positions = new long[Zone.VALUES.length][];
		for (int i = 0; i < positions.length; i++) {
			// A different count per zone, so zones swapped on the way would not decode equal.
			positions[i] = new long[i];
			for (int j = 0; j < i; j++) {
				positions[i][j] = BlockPos.asLong(-48 + j, -59 + i, 32);
			}
		}
		ChunkResultPayload payload = new ChunkResultPayload(42, -3, 2, positions);

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ChunkResultPayload.CODEC.encode(buf, payload);
		ChunkResultPayload decoded = ChunkResultPayload.CODEC.decode(buf);

		assertEquals(0, buf.readableBytes(), "bytes left after decoding");
		assertEquals(42, decoded.scanId());
		assertEquals(-3, decoded.chunkX());
		assertEquals(2, decoded.chunkZ());
		assertArrayEquals(positions, decoded.positions());
	}

	@Test
	void needsOneArrayPerZone() {
		assertThrows(IllegalArgumentException.class, () -> new ChunkResultPayload(1, 0, 0, new long[1][]));
	}
}
