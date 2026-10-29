package com.panyaaa256.periscan.scan;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The config values a scan depends on. The client builds it from its config
 * (PeriScanConfig, which only exists on the client) and sends it to the server
 * with a scan request, so the zones and matchers can be compiled from it on
 * either side. Display settings (colors, visibility) are not part of it.
 *
 * @param northSouthWidth trench thickness along Z
 * @param eastWestWidth   trench thickness along X
 * @param scanMaxY        highest scanned Y (inclusive), before clamping to the world
 * @param bottomTrench    whether the bottom trench zone is scanned
 * @param fenceBlocks     trench inner entries only highlighted in the fence lanes
 */
public record ScanSettings(int northSouthWidth, int eastWestWidth, int scanMaxY,
		BlockZone trenchOuter, BlockZone trenchInner, boolean bottomTrench, BlockZone eater,
		List<String> fenceBlocks, int fallingRunLength, boolean eaterIncludeTrench,
		boolean waterloggedExcludePushDestroy, List<String> waterloggedBlacklist) {

	private static final StreamCodec<ByteBuf, List<String>> ENTRIES = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

	public static final StreamCodec<FriendlyByteBuf, ScanSettings> STREAM_CODEC =
			StreamCodec.ofMember(ScanSettings::write, ScanSettings::read);

	/** A zone highlighting configured blocks (block ids or tags, as in the config). */
	public record BlockZone(boolean enabled, List<String> blocks, boolean waterlogged) {

		private void write(FriendlyByteBuf buf) {
			buf.writeBoolean(enabled);
			ENTRIES.encode(buf, blocks);
			buf.writeBoolean(waterlogged);
		}

		private static BlockZone read(FriendlyByteBuf buf) {
			return new BlockZone(buf.readBoolean(), readEntries(buf), buf.readBoolean());
		}
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(northSouthWidth);
		buf.writeVarInt(eastWestWidth);
		buf.writeVarInt(scanMaxY);
		trenchOuter.write(buf);
		trenchInner.write(buf);
		buf.writeBoolean(bottomTrench);
		eater.write(buf);
		ENTRIES.encode(buf, fenceBlocks);
		buf.writeVarInt(fallingRunLength);
		buf.writeBoolean(eaterIncludeTrench);
		buf.writeBoolean(waterloggedExcludePushDestroy);
		ENTRIES.encode(buf, waterloggedBlacklist);
	}

	private static ScanSettings read(FriendlyByteBuf buf) {
		return new ScanSettings(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				BlockZone.read(buf), BlockZone.read(buf), buf.readBoolean(), BlockZone.read(buf),
				readEntries(buf), buf.readVarInt(), buf.readBoolean(),
				buf.readBoolean(), readEntries(buf));
	}

	// Copied, like PeriScanConfig.scanSettings() does, so the lists cannot change.
	private static List<String> readEntries(FriendlyByteBuf buf) {
		return List.copyOf(ENTRIES.decode(buf));
	}
}
