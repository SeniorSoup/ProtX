package com.serverx.core;

import com.mojang.brigadier.CommandDispatcher;
import com.serverx.ServerX;
import com.serverx.api.ServerXApi;
import com.serverx.api.ServerXModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Owns the module list and runs each lifecycle step, so one broken module cannot take down the rest. */
public final class ModuleManager {

	private final ServerXApi api;
	private final Map<String, ServerXModule> modules = new LinkedHashMap<>();

	public ModuleManager(ServerXApi api) {
		this.api = api;
	}

	public void register(ServerXModule module) {
		if (modules.putIfAbsent(module.id(), module) != null) {
			ServerX.LOGGER.warn("Ignoring module with duplicate id '{}'", module.id());
		}
	}

	/** Picks up modules that other mods declare under the "serverx" entrypoint. */
	public void loadExtensions() {
		for (ServerXModule module : FabricLoader.getInstance().getEntrypoints("serverx", ServerXModule.class)) {
			register(module);
			ServerX.LOGGER.info("Loaded ServerX extension '{}'", module.id());
		}
	}

	public Collection<ServerXModule> all() {
		return modules.values();
	}

	public void init() {
		each("init", module -> module.onInit(api));
	}

	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		each("registerCommands", module -> module.registerCommands(dispatcher, api));
	}

	public void enable() {
		each("enable", module -> module.onEnable(api));
	}

	public void disable() {
		each("disable", module -> module.onDisable(api));
	}

	public void reload() {
		api.reloadConfig();
		each("reload", module -> module.onReload(api));
	}

	private void each(String step, Consumer<ServerXModule> action) {
		for (ServerXModule module : modules.values()) {
			try {
				action.accept(module);
			} catch (Throwable t) {
				ServerX.LOGGER.error("Module '{}' failed during {}", module.id(), step, t);
			}
		}
	}
}
