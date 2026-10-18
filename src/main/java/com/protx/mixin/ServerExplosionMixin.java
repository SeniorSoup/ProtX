package com.protx.mixin;

import com.protx.ProtX;
import com.protx.protlib.ProtLib;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Removes blocks inside explosion-proof regions from the list of blocks an explosion is about to
 * destroy. Creepers, TNT, TNT minecarts, end crystals, ghast fireballs and so on all go through here.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void protx$spareProtectedBlocks(CallbackInfoReturnable<List<BlockPos>> cir) {
		ProtLib protLib = ProtX.api().findService(ProtLib.class).orElse(null);
		if (protLib == null) {
			return;
		}
		cir.setReturnValue(protLib.filterExplosion(this.level, cir.getReturnValue()));
	}
}
