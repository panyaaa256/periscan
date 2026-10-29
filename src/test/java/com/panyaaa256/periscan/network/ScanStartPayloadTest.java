package com.panyaaa256.periscan.network;

import com.panyaaa256.periscan.scan.ScanSettings;
import com.panyaaa256.periscan.scan.ScanSettings.BlockZone;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanStartPayloadTest {
	// ChunkPos and the dimension keys need the registries (see PeriProfileTest).
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static ScanStartPayload roundTrip(ScanStartPayload payload) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ScanStartPayload.CODEC.encode(buf, payload);
		ScanStartPayload decoded = ScanStartPayload.CODEC.decode(buf);
		assertEquals(0, buf.readableBytes(), "bytes left after decoding");
		return decoded;
	}

	@Test
	void everyValueSurvivesTheRoundTrip() {
		// Each value differs from its neighbors, so swapped fields would not decode equal.
		ScanSettings settings = new ScanSettings(12, 3, -40,
				new BlockZone(true, List.of("minecraft:lava"), false),
				new BlockZone(false, List.of("#periscan:immovable", "minecraft:obsidian"), true),
				true,
				new BlockZone(true, List.of(), true),
				List.of("#minecraft:walls"), 10, false,
				true, List.of("minecraft:oak_stairs"));
		ScanStartPayload payload = new ScanStartPayload(Level.NETHER, new ChunkPos(-3, 7), new ChunkPos(12, -40), settings);

		assertEquals(payload, roundTrip(payload));
	}

	@Test
	void longBlockListsSurviveTheRoundTrip() {
		// Encodes to far more than a vanilla serverbound packet (32767 bytes): the
		// codec itself must not limit the lists. Splitting into packets is up to
		// Fabric API (registerLarge) and is not tested here.
		List<String> entries = Collections.nCopies(2000, "minecraft:polished_blackstone_brick_stairs");
		BlockZone zone = new BlockZone(true, entries, false);
		ScanSettings settings = new ScanSettings(12, 3, 128, zone, zone, false, zone,
				entries, 10, false, true, entries);
		ScanStartPayload payload = new ScanStartPayload(Level.OVERWORLD, new ChunkPos(0, 0), new ChunkPos(31, 31), settings);

		assertEquals(payload, roundTrip(payload));
	}
}
