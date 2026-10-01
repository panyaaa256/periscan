package com.panyaaa256.periscan.integration.litematica;

import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.PlannedPlacement;
import com.panyaaa256.periscan.integration.litematica.LitematicaIntegration.Result;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only class that touches litematica types. It must not be class-loaded
 * unless LitematicaIntegration#isAvailable() has passed; everything it uses is
 * covered by the capability probe there.
 */
final class LitematicaPlacer {
	/** Discards litematica's setRotation feedback; we report the outcome ourselves. */
	private static final IMessageConsumer NO_MESSAGES = new IMessageConsumer() {
		@Override
		public void addMessage(Message.MessageType type, String messageKey, Object... args) {
		}

		@Override
		public void addMessage(Message.MessageType type, int lifeTime, String messageKey, Object... args) {
		}
	};

	private LitematicaPlacer() {
	}

	static Path schematicsBaseDirectory() {
		return DataManager.getSchematicsBaseDirectory();
	}

	/** The name in the schematic file's metadata; null if the file cannot be read. */
	static String schematicName(Path dir, String fileName) {
		SchematicMetadata metadata = LitematicaSchematic.readMetadataFromFile(dir, fileName);
		return metadata == null ? null : metadata.getName();
	}

	static Result place(List<PlannedPlacement> plan, String removePrefix) {
		// Load every schematic before touching any placement, so a broken file
		// aborts the whole batch: all succeed or nothing happens.
		Map<PlannedPlacement, LitematicaSchematic> loaded = new LinkedHashMap<>();
		for (PlannedPlacement planned : plan) {
			LitematicaSchematic schematic;
			try {
				schematic = LitematicaSchematic.createFromFile(planned.dir(), planned.fileName());
			} catch (Exception e) {
				schematic = null;
			}
			if (schematic == null) {
				return new Result(0, 0, planned.fileName());
			}
			loaded.put(planned, schematic);
		}

		int removed = removeWithPrefix(removePrefix);
		SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
		for (Map.Entry<PlannedPlacement, LitematicaSchematic> entry : loaded.entrySet()) {
			PlannedPlacement planned = entry.getKey();
			SchematicPlacement placement = SchematicPlacement.createFor(
					entry.getValue(), planned.origin(), planned.placementName(), true, true);
			manager.addSchematicPlacement(placement, false);
			// Transform after adding (mirroring the GUI flow, so the schematic
			// world refreshes) and lock last: a locked placement refuses modification.
			if (planned.rotation() != Rotation.NONE) {
				placement.setRotation(planned.rotation(), NO_MESSAGES);
			}
			if (planned.mirror() != Mirror.NONE) {
				placement.setMirror(planned.mirror(), NO_MESSAGES);
			}
			if (!placement.isLocked()) {
				placement.toggleLocked();
			}
		}
		return new Result(loaded.size(), removed, null);
	}

	static int removeWithPrefix(String prefix) {
		SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
		List<SchematicPlacement> toRemove = new ArrayList<>();
		for (SchematicPlacement placement : manager.getAllSchematicsPlacements()) {
			if (placement.getName().startsWith(prefix)) {
				toRemove.add(placement);
			}
		}
		for (SchematicPlacement placement : toRemove) {
			manager.removeSchematicPlacement(placement);
		}
		return toRemove.size();
	}
}
