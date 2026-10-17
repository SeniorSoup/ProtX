package com.serverx.core;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

/**
 * One permission node, checked through Fabric API's permissions API. LuckPerms answers these
 * checks; if no permission manager has an opinion, the vanilla op level in {@code fallback}
 * decides (or "deny" for flag-style nodes).
 *
 * <p>The id {@code serverx:protect.make} shows up in LuckPerms as {@code serverx.protect.make}.
 */
public final class Perm {

	private final Identifier id;
	private final PermissionLevel fallback; // null = denied unless explicitly granted

	private Perm(Identifier id, PermissionLevel fallback) {
		this.id = id;
		this.fallback = fallback;
	}

	/** A node that vanilla ops of at least {@code fallback} get automatically. */
	public static Perm of(String path, PermissionLevel fallback) {
		return new Perm(Identifier.fromNamespaceAndPath("serverx", path), fallback);
	}

	/** A node nobody has unless it is explicitly granted (e.g. immunity flags). */
	public static Perm flag(String path) {
		return new Perm(Identifier.fromNamespaceAndPath("serverx", path), null);
	}

	public boolean has(CommandSourceStack source) {
		return fallback == null ? source.checkPermission(id, false) : source.checkPermission(id, fallback);
	}

	public boolean has(ServerPlayer player) {
		return has(player.createCommandSourceStack());
	}

	public Predicate<CommandSourceStack> predicate() {
		return this::has;
	}

	/** The node as typed into LuckPerms, e.g. "serverx.protect.make". */
	public String node() {
		return id.getNamespace() + "." + id.getPath();
	}
}
