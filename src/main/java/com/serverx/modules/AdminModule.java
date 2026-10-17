package com.serverx.modules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.serverx.ServerX;
import com.serverx.api.ServerXApi;
import com.serverx.api.ServerXModule;
import com.serverx.core.Msg;
import com.serverx.core.Perms;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

/** /cmd (run a command as the server console) and /serverx (status, reload). */
public final class AdminModule implements ServerXModule {

	private ServerXApi api;

	@Override
	public String id() {
		return "admin";
	}

	@Override
	public void onInit(ServerXApi api) {
		this.api = api;
	}

	@Override
	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ServerXApi api) {
		// Runs the text exactly as if it were typed into the server panel / console.
		dispatcher.register(Commands.literal("cmd").requires(Perms.CMD.predicate())
			.then(Commands.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
				CommandSourceStack source = ctx.getSource();
				String command = StringArgumentType.getString(ctx, "command").trim();
				if (command.startsWith("/")) {
					command = command.substring(1);
				}
				if (command.isEmpty()) {
					Msg.failure(source, "Usage: /cmd <command>");
					return 0;
				}
				MinecraftServer server = source.getServer();
				if (api.config().auditCmd) {
					ServerX.LOGGER.warn("[/cmd] {} ran: /{}", source.getTextName(), command);
				}
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
				Msg.success(source, "Ran as the server: /" + command + " (its output appears in the server console)");
				return 1;
			})));

		dispatcher.register(Commands.literal("serverx").requires(Perms.ADMIN.predicate())
			.executes(ctx -> {
				String version = FabricLoader.getInstance().getModContainer(ServerX.MOD_ID)
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("?");
				StringBuilder modules = new StringBuilder();
				for (ServerXModule module : ServerX.modules().all()) {
					if (!modules.isEmpty()) {
						modules.append(", ");
					}
					modules.append(module.id());
				}
				Msg.success(ctx.getSource(), "ServerX " + version + " - modules: " + modules);
				return 1;
			})
			.then(Commands.literal("reload").executes(ctx -> {
				ServerX.modules().reload();
				Msg.success(ctx.getSource(), "ServerX reloaded (config, regions, prison).");
				return 1;
			})));
	}
}
