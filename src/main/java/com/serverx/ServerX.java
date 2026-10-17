package com.serverx;

import com.serverx.api.ServerXApi;
import com.serverx.core.ModuleManager;
import com.serverx.core.Msg;
import com.serverx.modules.AdminModule;
import com.serverx.modules.PrisonModule;
import com.serverx.modules.PrisonService;
import com.serverx.modules.ProtectModule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServerX entry point. It only wires things together - all features live in modules
 * (see {@link com.serverx.api.ServerXModule}). To add a feature, write a module and register it below.
 */
public final class ServerX implements ModInitializer {

	public static final String MOD_ID = "serverx";
	public static final Logger LOGGER = LoggerFactory.getLogger("ServerX");

	private static ServerXApi api;
	private static ModuleManager modules;

	public static ServerXApi api() {
		if (api == null) {
			throw new IllegalStateException("ServerX has not been initialised yet");
		}
		return api;
	}

	public static ModuleManager modules() {
		if (modules == null) {
			throw new IllegalStateException("ServerX has not been initialised yet");
		}
		return modules;
	}

	@Override
	public void onInitialize() {
		api = new ServerXApi(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
		modules = new ModuleManager(api);

		// Built-in modules. Add your own here: modules.register(new MyModule());
		modules.register(new AdminModule());
		modules.register(new PrisonModule());
		modules.register(new ProtectModule());
		// Modules from other mods (fabric.mod.json -> "entrypoints" -> "serverx")
		modules.loadExtensions();

		modules.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			modules.registerCommands(dispatcher));

		ServerLifecycleEvents.SERVER_STARTING.register(api::attach);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> modules.enable());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			modules.disable();
			api.detach();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> api.scheduler().tick());
		api.scheduler().repeat(10, api::pollPlayers);

		LOGGER.info("ServerX loaded with {} module(s)", modules.all().size());
	}

	/**
	 * Called from the command mixin before any player-issued command runs.
	 *
	 * @return true if the command must be cancelled (the player is in prison)
	 */
	public static boolean blockCommand(ServerPlayer player) {
		if (api == null) {
			return false;
		}
		PrisonService prison = api.findService(PrisonService.class).orElse(null);
		if (prison == null || !prison.isJailed(player.getUUID())) {
			return false;
		}
		api.notifyThrottled(player, Msg.err("You are in prison and cannot use commands."));
		return true;
	}
}
