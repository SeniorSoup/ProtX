package com.serverx.mixin;

import com.mojang.brigadier.ParseResults;
import com.serverx.ServerX;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops imprisoned players from running any command. Commands that ServerX itself runs as the
 * server (teleports, /cmd) have no player source, so they are never blocked.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void serverx$blockPrisonerCommands(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
		ServerPlayer player = parseResults.getContext().getSource().getPlayer();
		if (player != null && ServerX.blockCommand(player)) {
			ci.cancel();
		}
	}
}
