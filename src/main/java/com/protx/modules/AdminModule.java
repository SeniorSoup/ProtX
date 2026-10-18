package com.protx.modules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.protx.ProtX;
import com.protx.api.ProtXApi;
import com.protx.api.ProtXModule;
import com.protx.core.Msg;
import com.protx.core.Perms;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

/** /cmd (run a command as the server console) and /protx (status, reload). */
public final class AdminModule implements ProtXModule {

	private ProtXApi api;

	@Override
	public String id() {
		return "admin";
	}

	@Override
	public void onInit(ProtXApi api) {
		this.api = api;
	}

	@Override
	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ProtXApi api) {
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
					ProtX.LOGGER.warn("[/cmd] {} ran: /{}", source.getTextName(), command);
				}
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
				Msg.success(source, "Ran as the server: /" + command + " (its output appears in the server console)");
				return 1;
			})));

		dispatcher.register(Commands.literal("protx").requires(Perms.ADMIN.predicate())
			.executes(ctx -> {
				String version = FabricLoader.getInstance().getModContainer(ProtX.MOD_ID)
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("?");
				StringBuilder modules = new StringBuilder();
				for (ProtXModule module : ProtX.modules().all()) {
					if (!modules.isEmpty()) {
						modules.append(", ");
					}
					modules.append(module.id());
				}
				Msg.success(ctx.getSource(), "ProtX " + version + " - modules: " + modules);
				return 1;
			})
			.then(Commands.literal("reload").executes(ctx -> {
				ProtX.modules().reload();
				Msg.success(ctx.getSource(), "ProtX reloaded (config, regions, prison).");
				return 1;
			})));
	}
}
