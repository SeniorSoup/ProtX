package com.serverx.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** A saved position (dimension + coordinates + rotation). Plain fields so Gson can store it. */
public final class Location {

	public String dim;
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;

	public Location() {
	}

	public static Location of(ServerPlayer player) {
		Location loc = new Location();
		loc.dim = dimId(player.level());
		loc.x = player.getX();
		loc.y = player.getY();
		loc.z = player.getZ();
		loc.yaw = player.getYRot();
		loc.pitch = player.getXRot();
		return loc;
	}

	/** "minecraft:overworld", "minecraft:the_nether", ... */
	public static String dimId(Level level) {
		return level.dimension().identifier().toString();
	}

	public double distanceSqr(ServerPlayer player) {
		double dx = player.getX() - x;
		double dy = player.getY() - y;
		double dz = player.getZ() - z;
		return dx * dx + dy * dy + dz * dz;
	}

	public String describe() {
		return String.format("%.1f, %.1f, %.1f in %s", x, y, z, dim);
	}
}
