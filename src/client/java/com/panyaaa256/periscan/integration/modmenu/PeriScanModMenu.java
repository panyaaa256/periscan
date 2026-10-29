package com.panyaaa256.periscan.integration.modmenu;

import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.config.PeriScanConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu entrypoint: makes the "Configure" button open the config screen.
 * Only Mod Menu loads this class (via the "modmenu" entrypoint), so it is safe
 * without Mod Menu installed.
 */
public final class PeriScanModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// The screen is made with YACL, which is optional; null opens nothing.
		return parent -> PeriScanConfig.yaclLoaded() ? PeriScanConfigScreen.create(parent) : null;
	}
}
