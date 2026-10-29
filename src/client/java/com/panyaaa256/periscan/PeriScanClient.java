package com.panyaaa256.periscan;

import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.persist.ProfileStore;
import com.panyaaa256.periscan.render.HighlightRenderer;
import com.panyaaa256.periscan.scan.ScanManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

public class PeriScanClient implements ClientModInitializer {
	private static Supplier<Screen> scheduledScreen = null;

	@Override
	public void onInitializeClient() {
		if (!PeriScanConfig.yaclLoaded()) {
			PeriScan.LOGGER.warn("PeriScan: YetAnotherConfigLib is not installed; the default config is used and cannot be changed");
		} else if (PeriScanConfig.load()) {
			PeriScan.LOGGER.warn("PeriScan: the config file had invalid values; they were reset to the nearest allowed ones");
		}
		PeriCommand.register();
		PeriKeybinds.register();
		ScanManager.INSTANCE.init();
		HighlightRenderer.init();
		ProfileStore.init();

		// Screens cannot be opened directly from a command (the chat screen closes
		// afterwards and would override it), so open scheduled screens next tick.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (scheduledScreen != null && client.gui.screen() == null) {
				Screen screen = scheduledScreen.get();
				scheduledScreen = null;
				client.gui.setScreen(screen);
			}
		});
	}

	public static void scheduleScreen(Supplier<Screen> screen) {
		scheduledScreen = screen;
	}
}
