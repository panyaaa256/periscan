package com.panyaaa256.periscan;

import com.mojang.blaze3d.platform.InputConstants;
import com.panyaaa256.periscan.compat.VersionCompat;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.config.PeriScanConfig.ZoneSettings;
import com.panyaaa256.periscan.config.PeriScanConfigScreen;
import com.panyaaa256.periscan.zone.Zone;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The keys in the PeriScan category of the vanilla key binds screen. None has
 * a default key, so they do nothing until the player assigns one.
 */
public final class PeriKeybinds {
	// Before 1.21.9 a category is just its translation key (the same key 1.21.9+
	// derives from the category id, so the lang files need no change).
	private static final String CATEGORY = "key.category." + PeriScanClient.MOD_ID + ".general";

	// In registration order; each key runs its action once per press.
	private static final Map<KeyMapping, Consumer<Minecraft>> ACTIONS = new LinkedHashMap<>();

	private PeriKeybinds() {
	}

	public static void register() {
		add("open_config", client -> client.setScreen(PeriScanConfigScreen.create(null)));
		add("toggle_highlights", PeriKeybinds::toggleHighlights);
		add("rescan", PeriKeybinds::rescan);
		add("clear_scan", client -> PeriCommand.scanClear(chat(client.player)));
		for (Zone zone : Zone.VALUES) {
			add("toggle_zone." + zone.id(), client -> toggleZone(client, zone));
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// The messages need a player; keys are not pressed outside a world anyway.
			if (client.player == null || client.level == null) {
				return;
			}
			ACTIONS.forEach((key, action) -> {
				while (key.consumeClick()) {
					action.accept(client);
				}
			});
		});
	}

	private static void add(String name, Consumer<Minecraft> action) {
		KeyMapping key = new KeyMapping("key." + PeriScanClient.MOD_ID + "." + name,
				InputConstants.UNKNOWN.getValue(), CATEGORY);
		ACTIONS.put(KeyBindingHelper.registerKeyBinding(key), action);
	}

	private static Consumer<Component> chat(LocalPlayer player) {
		return message -> VersionCompat.sendChat(player, message);
	}

	/** Red, like the errors of the /peri command. */
	private static Component error(Component message) {
		return Component.empty().append(message).withStyle(ChatFormatting.RED);
	}

	private static void toggleHighlights(Minecraft client) {
		PeriScanConfig config = PeriScanConfig.get();
		config.showHighlights = !config.showHighlights;
		PeriScanConfig.handler().save();
		VersionCompat.sendOverlay(client.player, Component.translatable(
				config.showHighlights ? "periscan.msg.highlights_shown" : "periscan.msg.highlights_hidden"));
	}

	private static void rescan(Minecraft client) {
		Consumer<Component> chat = chat(client.player);
		PeriCommand.scanReload(client.level, chat, message -> chat.accept(error(message)));
	}

	private static void toggleZone(Minecraft client, Zone zone) {
		PeriScanConfig config = PeriScanConfig.get();
		ZoneSettings settings = zone.settings(config);
		Component name = Component.translatable("periscan.config.category." + zone.id());
		// A zone that is not scanned has no highlights to show or hide.
		if (!settings.enabled) {
			VersionCompat.sendOverlay(client.player, error(Component.translatable("periscan.msg.zone_not_scanned", name)));
			return;
		}
		settings.visible = !settings.visible;
		PeriScanConfig.handler().save();
		String message;
		if (!settings.visible) {
			message = "periscan.msg.zone_hidden";
		} else if (config.showHighlights) {
			message = "periscan.msg.zone_shown";
		} else {
			message = "periscan.msg.zone_shown_all_hidden";
		}
		VersionCompat.sendOverlay(client.player, Component.translatable(message, name));
	}
}
