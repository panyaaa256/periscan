package com.panyaaa256.periscan.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;

import java.util.stream.Stream;

/**
 * Calls whose shape (not just name) differs between the Minecraft versions this
 * branch builds. Keeping them here keeps version conditions out of the rest of
 * the code; plain renames are handled by replacements in stonecutter.gradle.kts.
 */
public final class VersionCompat {
	// Own id, so the toast does not replace (or get replaced by) a vanilla one.
	private static final SystemToast.SystemToastId INVALID_ENTRIES = new SystemToast.SystemToastId();

	private VersionCompat() {
	}

	/** Chunk X coordinate; ChunkPos became a record in 26.1. */
	public static int chunkX(ChunkPos pos) {
		//? if >=26.1 {
		/*return pos.x();
		*///?} else
		return pos.x;
	}

	/** Chunk Z coordinate; ChunkPos became a record in 26.1. */
	public static int chunkZ(ChunkPos pos) {
		//? if >=26.1 {
		/*return pos.z();
		*///?} else
		return pos.z;
	}

	/** The chunk position packed into a long, as used by ChunkPos.getX/getZ(long). */
	public static long chunkKey(ChunkPos pos) {
		//? if >=26.1 {
		/*return pos.pack();
		*///?} else
		return pos.toLong();
	}

	/** Shows a message in the chat. */
	public static void sendChat(LocalPlayer player, Component message) {
		//? if >=26.1 {
		/*player.sendSystemMessage(message);
		*///?} else
		player.displayClientMessage(message, false);
	}

	/** Shows a message above the hotbar. */
	public static void sendOverlay(LocalPlayer player, Component message) {
		//? if >=26.1 {
		/*player.sendOverlayMessage(message);
		*///?} else
		player.displayClientMessage(message, true);
	}

	/** Shows a system toast (works outside a world, unlike chat). */
	public static void showToast(Component title, Component message) {
		//? if >=26.2 {
		ToastManager toasts = Minecraft.getInstance().gui.toastManager();
		//?} else
		//ToastManager toasts = Minecraft.getInstance().getToastManager();
		SystemToast.addOrUpdate(toasts, INVALID_ENTRIES, title, message);
	}

	/** The ids of the block tags currently bound; empty outside a world. */
	public static Stream<Identifier> blockTagIds() {
		// TagKey::location is a method reference on purpose: the 1.21.11 rename
		// replacement in stonecutter.gradle.kts (meant for ResourceKey) rewrites
		// direct calls, but TagKey keeps location() in every version.
		return BuiltInRegistries.BLOCK.getTags().map(named -> named.key()).map(TagKey::location);
	}
}
