package com.panyaaa256.periscan.integration.litematica;

import com.panyaaa256.periscan.PeriScanClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point of the litematica integration. This class never references
 * litematica types, so it is safe to load without litematica installed; all
 * direct access lives in {@link LitematicaPlacer}, which is only class-loaded
 * after {@link #isAvailable()} passed.
 */
public final class LitematicaIntegration {
	private static Boolean available;

	/** One placement to create: a schematic file plus where and how to place it. */
	public record PlannedPlacement(Path dir, String fileName, String placementName, BlockPos origin,
			Rotation rotation, Mirror mirror) {
	}

	/**
	 * Outcome of a batch. Either failedFile is null and the counts are valid, or
	 * it names the file that could not be loaded (and nothing was changed).
	 */
	public record Result(int created, int removed, String failedFile) {
	}

	private LitematicaIntegration() {
	}

	public static boolean isAvailable() {
		if (available == null) {
			available = probe();
		}
		return available;
	}

	/**
	 * Removes all placements whose name starts with removePrefix, then creates
	 * the planned batch; aborts without touching anything if a file fails to
	 * load. Caller must have checked {@link #isAvailable()}.
	 */
	public static Result place(List<PlannedPlacement> plan, String removePrefix) {
		return LitematicaPlacer.place(plan, removePrefix);
	}

	/**
	 * Removes all placements whose name starts with the prefix, returning how
	 * many. Caller must have checked {@link #isAvailable()}.
	 */
	public static int removePlacements(String prefix) {
		return LitematicaPlacer.removeWithPrefix(prefix);
	}

	/** Litematica's schematics directory (its config can move it). Caller must have checked {@link #isAvailable()}. */
	public static Path schematicsBaseDirectory() {
		return LitematicaPlacer.schematicsBaseDirectory();
	}

	private static boolean probe() {
		if (!FabricLoader.getInstance().isModLoaded("litematica")) {
			return false;
		}
		// Capability probe instead of a version check: fork version schemes vary,
		// so verify the exact classes and methods LitematicaPlacer uses. Keep in
		// sync with the probe table in docs/placement.md.
		try {
			Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
			dataManager.getMethod("getSchematicPlacementManager");
			dataManager.getMethod("getSchematicsBaseDirectory");
			Class<?> schematic = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
			schematic.getMethod("createFromFile", Path.class, String.class);
			Class<?> messageConsumer = Class.forName("fi.dy.masa.malilib.gui.interfaces.IMessageConsumer");
			Class<?> placement = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
			placement.getMethod("createFor", schematic, BlockPos.class, String.class, boolean.class, boolean.class);
			placement.getMethod("setRotation", Rotation.class, messageConsumer);
			placement.getMethod("setMirror", Mirror.class, messageConsumer);
			placement.getMethod("isLocked");
			placement.getMethod("toggleLocked");
			placement.getMethod("getName");
			Class<?> manager = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager");
			manager.getMethod("addSchematicPlacement", placement, boolean.class);
			manager.getMethod("getAllSchematicsPlacements");
			manager.getMethod("removeSchematicPlacement", placement);
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			PeriScanClient.LOGGER.warn("PeriScan: litematica is present but incompatible, schematic placement disabled: {}",
					e.toString());
			return false;
		}
	}
}
