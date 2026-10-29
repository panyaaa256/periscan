package com.panyaaa256.periscan.server.mixin;

import com.panyaaa256.periscan.server.ServerScanManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API has no event for block changes on the server, so this hooks the
 * one place every change of a loaded chunk goes through: LevelChunk#setBlockState
 * (players, pistons, falling blocks, explosions, commands, ...); only world
 * generation writes the sections without it. At HEAD rather than RETURN, as
 * it returns null for some changes that did happen; the block is read again at
 * the end of the tick anyway.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void periscan$onSetBlockState(BlockPos pos, BlockState state, int flags,
			CallbackInfoReturnable<BlockState> cir) {
		if (((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
			ServerScanManager.onBlockChanged(level, pos);
		}
	}
}
