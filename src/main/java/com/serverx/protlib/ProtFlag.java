package com.serverx.protlib;

/**
 * A per-region switch. All built-in flags are "protection on" when true. Extensions can add their
 * own with {@link ProtLib#registerFlag(ProtFlag)}; they then show up in /protect edit and
 * /protect info automatically.
 */
public record ProtFlag(String id, boolean defaultValue, String description) {

	public static final ProtFlag EXPLOSION_PROOF = new ProtFlag("explosion-proof", true,
		"Creepers, TNT, crystals etc. cannot destroy blocks in the region");
	public static final ProtFlag NO_BREAK = new ProtFlag("no-break", true,
		"Players cannot break blocks (blocks they placed that are still decaying excepted)");
	public static final ProtFlag NO_PLACE = new ProtFlag("no-place", false,
		"Players cannot place blocks at all (overrides block-decay)");
	public static final ProtFlag BLOCK_DECAY = new ProtFlag("block-decay", true,
		"Blocks placed in the region disappear after the decay time");
	public static final ProtFlag LOCK_SIGNS = new ProtFlag("lock-signs", true,
		"Signs cannot be edited, dyed or waxed");
	public static final ProtFlag ALLOW_INTERACT = new ProtFlag("allow-interact", true,
		"Chests, barrels, doors, trapdoors, buttons, crafting tables etc. can be used");
}
