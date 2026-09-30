package com.panyaaa256.periscan.config;

import com.panyaaa256.periscan.compat.VersionCompat;
import com.panyaaa256.periscan.zone.ZoneMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Suggestions for the block lists of the config screen (see {@link ZoneMatcher} for the entry syntax). */
public final class BlockEntrySuggestions {
	private BlockEntrySuggestions() {
	}

	/** Every block id (including blocks without an item, e.g. minecraft:lava), sorted. */
	public static List<String> blockIds() {
		return BuiltInRegistries.BLOCK.keySet().stream().map(ResourceLocation::toString).sorted().toList();
	}

	/**
	 * The pseudo-tags, plus the world's block tags when {@code includeWorldTags}:
	 * block tags are only bound client side while in a world.
	 */
	public static List<String> tagEntries(boolean includeWorldTags) {
		List<String> tags = new ArrayList<>(ZoneMatcher.PSEUDO_TAG_ENTRIES);
		if (includeWorldTags) {
			Stream<String> worldTags = VersionCompat.blockTagIds().map(id -> "#" + id);
			tags.addAll(worldTags.sorted().toList());
		}
		return tags;
	}

	/** Tags first (they are fewer), then block ids. */
	public static List<String> all(boolean includeWorldTags) {
		List<String> all = new ArrayList<>(tagEntries(includeWorldTags));
		all.addAll(blockIds());
		return all;
	}
}
