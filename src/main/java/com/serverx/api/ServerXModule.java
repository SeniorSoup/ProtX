package com.serverx.api;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * A ServerX module. Every feature of ServerX (protect, prison, admin) is a module, and
 * extensions are just more modules.
 *
 * <p>Add one of your own in two ways:
 * <ol>
 *   <li>Inside this project: write a class implementing this interface and add
 *       {@code modules.register(new MyModule());} in {@code ServerX#onInitialize}.</li>
 *   <li>From a separate Fabric mod: implement this interface and declare it in that mod's
 *       fabric.mod.json under {@code "entrypoints": { "serverx": ["com.me.MyModule"] }}.</li>
 * </ol>
 * Lifecycle order: {@link #onInit} (mod load) -> {@link #registerCommands} (command
 * registration) -> {@link #onEnable} (server started) -> {@link #onDisable} (server stopping).
 */
public interface ServerXModule {

	/** Unique id, e.g. "protect". Used in /serverx. */
	String id();

	/** Called once while mods load. Register Fabric events and publish services here. */
	default void onInit(ServerXApi api) {
	}

	/** Called whenever commands are (re)registered. */
	default void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ServerXApi api) {
	}

	/** Called after the server has started. Load saved data here. */
	default void onEnable(ServerXApi api) {
	}

	/** Called when the server is stopping. Save data here. */
	default void onDisable(ServerXApi api) {
	}

	/** Called by /serverx reload. */
	default void onReload(ServerXApi api) {
	}
}
