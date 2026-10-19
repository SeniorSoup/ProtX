package com.protx;

import com.protx.api.ProtXApi;
import com.protx.core.ModuleManager;
import com.protx.core.Msg;
import com.protx.modules.AdminModule;
import com.protx.modules.PrisonModule;
import com.protx.modules.PrisonService;
import com.protx.modules.ProtectModule;
import com.protx.modules.ToolsModule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProtX entry point. It only wires things together - all features live in modules
 * (see {@link com.protx.api.ProtXModule}). To add a feature, write a module and register it below.
 */
public final class ProtX implements ModInitializer {

	public static final String MOD_ID = "protx";
	public static final Logger LOGGER = LoggerFactory.getLogger("ProtX");

	private static ProtXApi api;
	private static ModuleManager modules;

	public static ProtXApi api() {
		if (api == null) {
			throw new IllegalStateException("ProtX has not been initialised yet");
		}
		return api;
	}

	public static ModuleManager modules() {
		if (modules == null) {
			throw new IllegalStateException("ProtX has not been initialised yet");
		}
		return modules;
	}

	@Override
	public void onInitialize() {
		api = new ProtXApi(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
		modules = new ModuleManager(api);

		// Built-in modules. Add your own here: modules.register(new MyModule());
		modules.register(new AdminModule());
		modules.register(new PrisonModule());
		modules.register(new ProtectModule());
		modules.register(new ToolsModule());
		// Modules from other mods (fabric.mod.json -> "entrypoints" -> "protx")
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

		LOGGER.info("ProtX loaded with {} module(s)", modules.all().size());
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
