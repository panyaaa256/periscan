package com.panyaaa256.periscan.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * Calls whose shape (not just name) differs between the Minecraft versions this
 * branch builds. Keeping them here keeps version conditions out of the rest of
 * the code; plain renames are handled by replacements in stonecutter.gradle.kts.
 */
public final class VersionCompat {
	private VersionCompat() {
	}

	/** Chunk X coordinate; ChunkPos became a record in 26.1. */
	public static int chunkX(ChunkPos pos) {
		//? if >=26.1 {
		return pos.x();
		//?} else
		//return pos.x;
	}

	/** Chunk Z coordinate; ChunkPos became a record in 26.1. */
	public static int chunkZ(ChunkPos pos) {
		//? if >=26.1 {
		return pos.z();
		//?} else
		//return pos.z;
	}

	/** The chunk position packed into a long, as used by ChunkPos.getX/getZ(long). */
	public static long chunkKey(ChunkPos pos) {
		//? if >=26.1 {
		return pos.pack();
		//?} else
		//return pos.toLong();
	}

	/** Shows a message in the chat. */
	public static void sendChat(LocalPlayer player, Component message) {
		//? if >=26.1 {
		player.sendSystemMessage(message);
		//?} else
		//player.displayClientMessage(message, false);
	}

	/** Shows a message above the hotbar. */
	public static void sendOverlay(LocalPlayer player, Component message) {
		//? if >=26.1 {
		player.sendOverlayMessage(message);
		//?} else
		//player.displayClientMessage(message, true);
	}
}
