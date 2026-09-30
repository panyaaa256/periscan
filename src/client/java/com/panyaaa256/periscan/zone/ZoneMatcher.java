package com.panyaaa256.periscan.zone;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
// Copper bulbs and crafters were added in 1.20.3.
//? if >=1.20.3 {
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
//?}
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

	// Pseudo-tags resolved from block properties instead of real data tags,
	// because mod-provided tags are not synced when joining vanilla servers.
	// Every block a piston cannot move.
	public static final String IMMOVABLE_ENTRY = "#periscan:immovable";
	// Blocks whose shape connects to horizontal neighbors (walls, fences, panes, bars).
	public static final String CONNECTING_ENTRY = "#periscan:connecting";
	// Blocks whose state reacts to redstone signals (bulbs, pistons, trapdoors, ...).
	public static final String REDSTONE_REACTIVE_ENTRY = "#periscan:redstone_reactive";
	/** All pseudo-tag entries, for suggestions. */
	public static final List<String> PSEUDO_TAG_ENTRIES =
			List.of(IMMOVABLE_ENTRY, CONNECTING_ENTRY, REDSTONE_REACTIVE_ENTRY);

	/** The entries of {@code entries} that are neither a block id, a tag nor a pseudo-tag. */
	public static List<String> invalidEntries(List<String> entries) {
		List<String> invalid = new ArrayList<>();
		BlockSet.compile(entries, invalid);
		return invalid;
	}

	private static final class BlockSet {
		private final Set<Block> blocks = new HashSet<>();
		private final List<TagKey<Block>> tags = new ArrayList<>();
		// The config entry each tag came from, for messages.
		private final List<String> tagEntries = new ArrayList<>();
		private boolean immovable;
		private boolean connecting;
		private boolean redstoneReactive;

		static BlockSet compile(List<String> entries, List<String> invalidEntries) {
			BlockSet set = new BlockSet();
			for (String raw : entries) {
				String entry = raw.trim();
				if (entry.isEmpty()) {
					continue;
				}
				if (entry.equals(IMMOVABLE_ENTRY)) {
					set.immovable = true;
				} else if (entry.equals(CONNECTING_ENTRY)) {
					set.connecting = true;
				} else if (entry.equals(REDSTONE_REACTIVE_ENTRY)) {
					set.redstoneReactive = true;
				} else if (entry.startsWith("#")) {
					ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
					if (id == null) {
						invalidEntries.add(raw);
					} else {
						// Tag contents are not statically known client side; unknown tags simply match nothing.
						set.tags.add(TagKey.create(Registries.BLOCK, id));
						set.tagEntries.add(entry);
					}
				} else {
					ResourceLocation id = ResourceLocation.tryParse(entry);
					if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
						invalidEntries.add(raw);
					} else {
						set.blocks.add(BuiltInRegistries.BLOCK.get(id));
					}
				}
			}
			return set;
		}

		void collectTags(Map<TagKey<Block>, String> out) {
			for (int i = 0; i < tags.size(); i++) {
				out.putIfAbsent(tags.get(i), tagEntries.get(i));
			}
		}

		boolean matches(BlockState state) {
			if (immovable && isImmovable(state)) {
				return true;
			}
			if (connecting && isConnecting(state)) {
				return true;
			}
			if (redstoneReactive && isRedstoneReactive(state)) {
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

		/**
		 * A piston cannot push this block. Mirrors the state-only checks of vanilla
		 * PistonBaseBlock.isPushable, in the same order: obsidian, crying obsidian,
		 * respawn anchors and reinforced deepslate (hard-coded there, not a push
		 * reaction), extended (but not retracted) pistons, unbreakable blocks,
		 * blocks refusing the push, and blocks with a block entity (chests,
		 * spawners, sculk sensors, ...). The push reaction is decided before the
		 * block entity, so blocks a push destroys (bells, comparators, heads,
		 * decorated pots, ...) are not immovable even with a block entity. Bedrock
		 * is exempt: it is terrain (nether ceiling/floor), not an obstruction
		 * anyone placed.
		 */
		private static boolean isImmovable(BlockState state) {
			if (state.is(Blocks.BEDROCK)) {
				return false;
			}
			if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
					|| state.is(Blocks.RESPAWN_ANCHOR) || state.is(Blocks.REINFORCED_DEEPSLATE)) {
				return true;
			}
			if (state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)) {
				// Pistons refuse pushes by push reaction, but vanilla lets retracted
				// ones move (and they carry no block entity).
				return state.getValue(BlockStateProperties.EXTENDED);
			}
			if (state.getBlock().defaultDestroyTime() == -1.0F) {
				return true;
			}
			return switch (state.getPistonPushReaction()) {
				case BLOCK -> true;
				// Destroyed by the push, or pushable in one direction (glazed terracotta).
				case DESTROY, PUSH_ONLY -> false;
				default -> state.hasBlockEntity();
			};
		}

		/**
		 * The block's shape connects to horizontal neighbors, so placing a block
		 * next to it changes its state: fences, glass panes and iron bars
		 * (CrossCollisionBlock) and walls.
		 */
		private static boolean isConnecting(BlockState state) {
			Block block = state.getBlock();
			return block instanceof CrossCollisionBlock || block instanceof WallBlock;
		}

		/**
		 * The block's state changes when it receives a redstone signal (vanilla
		 * reads the signal in neighborChanged/tick and updates the state). Blocks
		 * that only emit a signal (buttons, levers, pressure plates, tripwires,
		 * observers, lecterns) or keep the reaction in a block entity (command and
		 * structure blocks) are not included. Rails switch shape at T-junctions
		 * and TNT turns into an entity.
		 */
		private static boolean isRedstoneReactive(BlockState state) {
			Block block = state.getBlock();
			return block instanceof DoorBlock
					|| block instanceof TrapDoorBlock
					|| block instanceof FenceGateBlock
					//? if >=1.20.3
					|| block instanceof CopperBulbBlock
					|| block instanceof PistonBaseBlock
					|| block instanceof DiodeBlock // repeaters, comparators
					|| block instanceof RedStoneWireBlock
					|| block instanceof RedstoneTorchBlock // includes wall torches
					|| block instanceof RedstoneLampBlock
					|| block instanceof DispenserBlock // includes droppers
					//? if >=1.20.3
					|| block instanceof CrafterBlock
					|| block instanceof HopperBlock
					|| block instanceof NoteBlock
					|| block instanceof BellBlock
					|| block instanceof AbstractSkullBlock // heads animate while powered
					|| block instanceof BigDripleafBlock // a signal resets the tilt
					|| block instanceof RailBlock
					|| block instanceof PoweredRailBlock // includes activator rails
					|| block instanceof TntBlock;
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

		/** Adds the tags of the blacklist, each with the config entry it came from. */
		public void collectTags(Map<TagKey<Block>, String> out) {
			blacklist.collectTags(out);
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
	private final boolean everythingButLiquids;

	private ZoneMatcher(BlockSet main, BlockSet laneRestricted, LanePredicate lanePredicate,
			boolean includeWaterlogged, WaterloggedExclusions waterloggedExclusions) {
		this(main, laneRestricted, lanePredicate, includeWaterlogged, waterloggedExclusions, false);
	}

	private ZoneMatcher(BlockSet main, BlockSet laneRestricted, LanePredicate lanePredicate,
			boolean includeWaterlogged, WaterloggedExclusions waterloggedExclusions, boolean everythingButLiquids) {
		this.main = main;
		this.laneRestricted = laneRestricted;
		this.lanePredicate = lanePredicate;
		this.includeWaterlogged = includeWaterlogged;
		this.waterloggedExclusions = waterloggedExclusions;
		this.everythingButLiquids = everythingButLiquids;
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

	/** Matcher that highlights every block except air and liquids (trench bottom). */
	public static ZoneMatcher everythingButLiquids() {
		return new ZoneMatcher(new BlockSet(), null, null, false, null, true);
	}

	/**
	 * Adds the tags this matcher looks for, each with the config entry it came
	 * from. Whether a tag exists is only known in a world, where the server has
	 * sent its tags.
	 */
	public void collectTags(Map<TagKey<Block>, String> out) {
		main.collectTags(out);
		if (laneRestricted != null) {
			laneRestricted.collectTags(out);
		}
	}

	public boolean matches(BlockState state, int x, int z) {
		if (everythingButLiquids) {
			return !state.isAir() && !(state.getBlock() instanceof LiquidBlock);
		}
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
