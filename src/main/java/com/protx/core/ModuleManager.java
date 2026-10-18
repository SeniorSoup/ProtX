package com.protx.core;

import com.mojang.brigadier.CommandDispatcher;
import com.protx.ProtX;
import com.protx.api.ProtXApi;
import com.protx.api.ProtXModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Owns the module list and runs each lifecycle step, so one broken module cannot take down the rest. */
public final class ModuleManager {

	private final ProtXApi api;
	private final Map<String, ProtXModule> modules = new LinkedHashMap<>();

	public ModuleManager(ProtXApi api) {
		this.api = api;
	}

	public void register(ProtXModule module) {
		if (modules.putIfAbsent(module.id(), module) != null) {
			ProtX.LOGGER.warn("Ignoring module with duplicate id '{}'", module.id());
		}
	}

	/** Picks up modules that other mods declare under the "protx" entrypoint. */
	public void loadExtensions() {
		for (ProtXModule module : FabricLoader.getInstance().getEntrypoints("protx", ProtXModule.class)) {
			register(module);
			ProtX.LOGGER.info("Loaded ProtX extension '{}'", module.id());
		}
	}

	public Collection<ProtXModule> all() {
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

	private void each(String step, Consumer<ProtXModule> action) {
		for (ProtXModule module : modules.values()) {
			try {
				action.accept(module);
			} catch (Throwable t) {
				ProtX.LOGGER.error("Module '{}' failed during {}", module.id(), step, t);
			}
		}
	}
}
