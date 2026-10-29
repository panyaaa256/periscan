package com.panyaaa256.periscan.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockUpdatesPayloadTest {
	@Test
	void everyValueSurvivesTheRoundTrip() {
		long[] positions = {BlockPos.asLong(1, -59, 2), BlockPos.asLong(-300, 128, 4000)};
		byte[] zoneMasks = {0, 0b1010};
		BlockUpdatesPayload payload = new BlockUpdatesPayload(5, positions, zoneMasks);

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		BlockUpdatesPayload.CODEC.encode(buf, payload);
		BlockUpdatesPayload decoded = BlockUpdatesPayload.CODEC.decode(buf);

		assertEquals(0, buf.readableBytes(), "bytes left after decoding");
		assertEquals(5, decoded.scanId());
		assertArrayEquals(positions, decoded.positions());
		assertArrayEquals(zoneMasks, decoded.zoneMasks());
	}

	@Test
	void needsOneMaskPerPosition() {
		assertThrows(IllegalArgumentException.class, () -> new BlockUpdatesPayload(1, new long[2], new byte[1]));
	}
}
