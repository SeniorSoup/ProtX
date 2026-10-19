package com.protx.core;

import net.minecraft.server.permissions.PermissionLevel;

/**
 * Every permission node ProtX uses. Grant them in LuckPerms, e.g.
 * {@code /lp group moderator permission set protx.prison true}.
 * The op level shown is what vanilla ops get when LuckPerms has no setting for the node.
 */
public final class Perms {

	private Perms() {
	}

	// remote console - extremely powerful, owners only by default
	public static final Perm CMD = Perm.of("cmd", PermissionLevel.OWNERS);

	// /protx
	public static final Perm ADMIN = Perm.of("admin", PermissionLevel.ADMINS);

	// /protect
	public static final Perm PROTECT_MAKE = Perm.of("protect.make", PermissionLevel.ADMINS);
	public static final Perm PROTECT_EDIT = Perm.of("protect.edit", PermissionLevel.ADMINS);
	public static final Perm PROTECT_DEL = Perm.of("protect.del", PermissionLevel.ADMINS);
	public static final Perm PROTECT_LIST = Perm.of("protect.list", PermissionLevel.GAMEMASTERS);
	public static final Perm PROTECT_HELP = Perm.of("protect.help", PermissionLevel.GAMEMASTERS);
	public static final Perm STRICTPROTECT = Perm.of("strictprotect", PermissionLevel.ADMINS);
	/** Build, break and interact inside protected regions without restrictions. */
	public static final Perm PROTECT_BYPASS = Perm.of("protect.bypass", PermissionLevel.ADMINS);

	// tools
	public static final Perm INV = Perm.of("inv", PermissionLevel.ADMINS);
	/** Lets /inv move items in the other player's inventory. Without it /inv is view-only. */
	public static final Perm INV_EDIT = Perm.of("inv.edit", PermissionLevel.ADMINS);
	public static final Perm BRING = Perm.of("bring", PermissionLevel.ADMINS);
	public static final Perm GOTO = Perm.of("goto", PermissionLevel.ADMINS);

	// prison
	public static final Perm PUNISHBAN = Perm.of("punishban", PermissionLevel.ADMINS);
	public static final Perm PARDONBAN = Perm.of("pardonban", PermissionLevel.ADMINS);
	public static final Perm PRISON = Perm.of("prison", PermissionLevel.GAMEMASTERS);
	public static final Perm PRISONIP = Perm.of("prisonip", PermissionLevel.GAMEMASTERS);
	public static final Perm PUNISHBANIP = Perm.of("punishbanip", PermissionLevel.ADMINS);
	public static final Perm PARDONIP = Perm.of("pardonip", PermissionLevel.ADMINS);
	public static final Perm SETPRISON = Perm.of("setprison", PermissionLevel.ADMINS);
	public static final Perm RMPRISON = Perm.of("rmprison", PermissionLevel.ADMINS);
	public static final Perm PRISONLIST = Perm.of("prisonlist", PermissionLevel.GAMEMASTERS);
	/** Players with this node cannot be imprisoned. Nobody has it by default. */
	public static final Perm PRISON_EXEMPT = Perm.flag("prison.exempt");
}
