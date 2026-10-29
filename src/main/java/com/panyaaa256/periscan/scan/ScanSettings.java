package com.panyaaa256.periscan.scan;

import java.util.List;

/**
 * The config values a scan depends on. The client builds it from its config
 * (PeriScanConfig, which only exists on the client), so the zones and matchers
 * can be compiled from it on either side. Display settings (colors, visibility)
 * are not part of it.
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

	/** A zone highlighting configured blocks (block ids or tags, as in the config). */
	public record BlockZone(boolean enabled, List<String> blocks, boolean waterlogged) {
	}
}
