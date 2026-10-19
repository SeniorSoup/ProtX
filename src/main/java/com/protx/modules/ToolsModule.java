package com.protx.modules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.protx.api.ProtXApi;
import com.protx.api.ProtXModule;
import com.protx.core.Msg;
import com.protx.core.Perms;
import com.protx.gui.InvViewer;
import com.protx.gui.PlayerPickerGui;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * /inv &lt;player&gt; (inventory viewer), /bring and /goto (pick an online player from a head GUI,
 * confirm with the emerald block, cancel with the barrier).
 */
public final class ToolsModule implements ProtXModule {

	@Override
	public String id() {
		return "tools";
	}

	@Override
	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ProtXApi api) {
		dispatcher.register(Commands.literal("inv").requires(Perms.INV.predicate())
			.then(Commands.argument("player", StringArgumentType.word()).suggests(ToolsModule::suggestOnline)
				.executes(this::inv)));

		dispatcher.register(Commands.literal("bring").requires(Perms.BRING.predicate())
			.executes(ctx -> picker(ctx, PlayerPickerGui.Mode.BRING)));

		dispatcher.register(Commands.literal("goto").requires(Perms.GOTO.predicate())
			.executes(ctx -> picker(ctx, PlayerPickerGui.Mode.GOTO)));
	}

	private int inv(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer staff = source.getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "player");
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
		if (target == null) {
			Msg.failure(source, "'" + name + "' is not online. /inv only works on online players.");
			return 0;
		}
		boolean editable = Perms.INV_EDIT.has(staff);
		InvViewer.open(staff, target, editable);
		return 1;
	}

	private int picker(CommandContext<CommandSourceStack> ctx, PlayerPickerGui.Mode mode) throws CommandSyntaxException {
		ServerPlayer staff = ctx.getSource().getPlayerOrException();
		PlayerPickerGui.open(ctx.getSource().getServer(), staff, mode);
		return 1;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOnline(
		CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		List<String> names = new ArrayList<>();
		for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
			names.add(player.getScoreboardName());
		}
		return SharedSuggestionProvider.suggest(names, builder);
	}
}
