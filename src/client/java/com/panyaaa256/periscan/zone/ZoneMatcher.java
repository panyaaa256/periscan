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

	/** Position gate for lane-restricted entries (walls/fences in the trench). */
	public interface LanePredicate {
		boolean test(int x, int z);
	}

	private static final class BlockSet {
		private final Set<Block> blocks = new HashSet<>();
		private final List<TagKey<Block>> tags = new ArrayList<>();

		static BlockSet compile(List<String> entries, List<String> invalidEntries) {
			BlockSet set = new BlockSet();
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
						set.tags.add(TagKey.create(Registries.BLOCK, id));
					}
				} else {
					Identifier id = Identifier.tryParse(entry);
					if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
						invalidEntries.add(raw);
					} else {
						set.blocks.add(BuiltInRegistries.BLOCK.getValue(id));
					}
				}
			}
			return set;
		}

		boolean matches(BlockState state) {
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

	/**
	 * Shared (all zones) exclusions for the waterlogged check: blocks that are
	 * destroyed when pushed by a piston (automatic, e.g. leaves / coral / pointed
	 * dripstone) and a manual blacklist. Only waterlogged blocks themselves are
	 * highlighted; plain water sources are not.
	 */
	public static final class WaterloggedExclusions {
		private final BlockSet blacklist;
		private final boolean excludePushDestroy;

		private WaterloggedExclusions(BlockSet blacklist, boolean excludePushDestroy) {
			this.blacklist = blacklist;
			this.excludePushDestroy = excludePushDestroy;
		}

		public static WaterloggedExclusions compile(List<String> blacklist, boolean excludePushDestroy, List<String> invalidEntries) {
			return new WaterloggedExclusions(BlockSet.compile(blacklist, invalidEntries), excludePushDestroy);
		}

		boolean excludes(BlockState state) {
			if (excludePushDestroy && state.getPistonPushReaction() == PushReaction.DESTROY) {
				return true;
			}
			return blacklist.matches(state);
		}
	}

	private final BlockSet main;
	private final BlockSet laneRestricted; // nullable
	private final LanePredicate lanePredicate;
	private final boolean includeWaterlogged;
	private final WaterloggedExclusions waterloggedExclusions;

	private ZoneMatcher(BlockSet main, BlockSet laneRestricted, LanePredicate lanePredicate,
			boolean includeWaterlogged, WaterloggedExclusions waterloggedExclusions) {
		this.main = main;
		this.laneRestricted = laneRestricted;
		this.lanePredicate = lanePredicate;
		this.includeWaterlogged = includeWaterlogged;
		this.waterloggedExclusions = waterloggedExclusions;
	}

	public static ZoneMatcher compile(List<String> entries, boolean includeWaterlogged,
			WaterloggedExclusions waterloggedExclusions, List<String> invalidEntries) {
		return new ZoneMatcher(BlockSet.compile(entries, invalidEntries), null, null,
				includeWaterlogged, waterloggedExclusions);
	}

	/**
	 * Variant with an extra block set that only matches in columns where
	 * {@code lanePredicate} is true (walls/fences in the trench body).
	 */
	public static ZoneMatcher compile(List<String> entries, List<String> laneEntries, LanePredicate lanePredicate,
			boolean includeWaterlogged, WaterloggedExclusions waterloggedExclusions, List<String> invalidEntries) {
		return new ZoneMatcher(BlockSet.compile(entries, invalidEntries),
				BlockSet.compile(laneEntries, invalidEntries), lanePredicate,
				includeWaterlogged, waterloggedExclusions);
	}

	public boolean matches(BlockState state, int x, int z) {
		if (main.matches(state)) {
			return true;
		}
		if (laneRestricted != null && lanePredicate.test(x, z) && laneRestricted.matches(state)) {
			return true;
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
