package com.panyaaa256.periscan;

import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.persist.ProfileStore;
import com.panyaaa256.periscan.render.HighlightRenderer;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class PeriScanClient implements ClientModInitializer {
	public static final String MOD_ID = "periscan";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Supplier<Screen> scheduledScreen = null;

	@Override
	public void onInitializeClient() {
		PeriScanConfig.handler().load();
		if (PeriScanConfig.get().sanitize()) {
			LOGGER.warn("PeriScan: the config file had invalid values; they were reset to the nearest allowed ones");
			PeriScanConfig.handler().save();
		}
		PeriCommand.register();
		PeriKeybinds.register();
		ScanManager.INSTANCE.init();
		HighlightRenderer.init();
		ProfileStore.init();

		// Screens cannot be opened directly from a command (the chat screen closes
		// afterwards and would override it), so open scheduled screens next tick.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (scheduledScreen != null && client.screen == null) {
				Screen screen = scheduledScreen.get();
				scheduledScreen = null;
				client.setScreen(screen);
			}
		});
	}

	public static void scheduleScreen(Supplier<Screen> screen) {
		scheduledScreen = screen;
	}

	/** An identifier in this mod's namespace. */
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
