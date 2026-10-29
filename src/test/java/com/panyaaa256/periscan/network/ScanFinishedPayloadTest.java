package com.panyaaa256.periscan.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanFinishedPayloadTest {
	@Test
	void everyValueSurvivesTheRoundTrip() {
		// Different lengths, so lists swapped on the way would not decode equal.
		long[] ungenerated = {1L, -2L, Long.MAX_VALUE};
		long[] unreadable = {Long.MIN_VALUE};
		ScanFinishedPayload payload = new ScanFinishedPayload(9, ungenerated, unreadable);

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ScanFinishedPayload.CODEC.encode(buf, payload);
		ScanFinishedPayload decoded = ScanFinishedPayload.CODEC.decode(buf);

		assertEquals(0, buf.readableBytes(), "bytes left after decoding");
		assertEquals(9, decoded.scanId());
		assertArrayEquals(ungenerated, decoded.ungenerated());
		assertArrayEquals(unreadable, decoded.unreadable());
	}
}
