package com.panyaaa256.periscan.mixin;

import com.panyaaa256.periscan.scan.ScanManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no client event for block changes, so this hooks the one place
 * every change goes through: Level#setBlock calls setBlocksDirty whenever a
 * block state really changed, whether it came from a server update packet
 * (single or section), the player's own predicted break/place, or anything else.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "setBlocksDirty", at = @At("HEAD"))
	private void periscan$onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		ScanManager.INSTANCE.onBlockChanged((ClientLevel) (Object) this, pos);
	}
}
