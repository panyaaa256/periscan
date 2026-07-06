package com.panyaaa256.periscan.zone;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiled matcher for one zone's config: block ids, #block tags and the
 * waterlogged toggle. Invalid entries are collected instead of throwing.
 */
public final class ZoneMatcher {

	/**
	 * Shared (all zones) exclusions for the waterlogged check: blocks that are
	 * destroyed when pushed by a piston (automatic, e.g. leaves / coral / pointed
	 * dripstone) and a manual blacklist. Only waterlogged blocks themselves are
	 * highlighted; plain water sources are not.
	 */
	public static final class WaterloggedExclusions {
		private final Set<Block> blocks;
		private final List<TagKey<Block>> tags;
		private final boolean excludePushDestroy;

		private WaterloggedExclusions(Set<Block> blocks, List<TagKey<Block>> tags, boolean excludePushDestroy) {
			this.blocks = blocks;
			this.tags = tags;
			this.excludePushDestroy = excludePushDestroy;
		}

		public static WaterloggedExclusions compile(List<String> blacklist, boolean excludePushDestroy, List<String> invalidEntries) {
			Set<Block> blocks = new HashSet<>();
			List<TagKey<Block>> tags = new ArrayList<>();
			parseEntries(blacklist, blocks, tags, invalidEntries);
			return new WaterloggedExclusions(blocks, tags, excludePushDestroy);
		}

		boolean excludes(BlockState state) {
			if (excludePushDestroy && state.getPistonPushReaction() == PushReaction.DESTROY) {
				return true;
			}
			if (blocks.contains(state.getBlock())) {
				return true;
			}
			for (TagKey<Block> tag : tags) {
				if (state.is(tag)) {
					return true;
				}
			}
			return false;
		}
	}

	private final Set<Block> blocks;
	private final List<TagKey<Block>> tags;
	private final boolean includeWaterlogged;
	private final WaterloggedExclusions waterloggedExclusions;

	private ZoneMatcher(Set<Block> blocks, List<TagKey<Block>> tags, boolean includeWaterlogged,
			WaterloggedExclusions waterloggedExclusions) {
		this.blocks = blocks;
		this.tags = tags;
		this.includeWaterlogged = includeWaterlogged;
		this.waterloggedExclusions = waterloggedExclusions;
	}

	public static ZoneMatcher compile(List<String> entries, boolean includeWaterlogged,
			WaterloggedExclusions waterloggedExclusions, List<String> invalidEntries) {
		Set<Block> blocks = new HashSet<>();
		List<TagKey<Block>> tags = new ArrayList<>();
		parseEntries(entries, blocks, tags, invalidEntries);
		return new ZoneMatcher(blocks, tags, includeWaterlogged, waterloggedExclusions);
	}

	private static void parseEntries(List<String> entries, Set<Block> blocks, List<TagKey<Block>> tags, List<String> invalidEntries) {
		for (String raw : entries) {
			String entry = raw.trim();
			if (entry.isEmpty()) {
				continue;
			}
			if (entry.startsWith("#")) {
				Identifier id = Identifier.tryParse(entry.substring(1));
				if (id == null) {
					invalidEntries.add(raw);
				} else {
					// Tag contents are not statically known client side; unknown tags simply match nothing.
					tags.add(TagKey.create(Registries.BLOCK, id));
				}
			} else {
				Identifier id = Identifier.tryParse(entry);
				if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
					invalidEntries.add(raw);
				} else {
					blocks.add(BuiltInRegistries.BLOCK.getValue(id));
				}
			}
		}
	}

	public boolean matches(BlockState state) {
		if (blocks.contains(state.getBlock())) {
			return true;
		}
		for (TagKey<Block> tag : tags) {
			if (state.is(tag)) {
				return true;
			}
		}
		if (includeWaterlogged
				&& state.hasProperty(BlockStateProperties.WATERLOGGED)
				&& state.getValue(BlockStateProperties.WATERLOGGED)
				&& !waterloggedExclusions.excludes(state)) {
			return true;
		}
		return false;
	}
}
