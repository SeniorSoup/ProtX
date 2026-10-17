package com.serverx.core;

import com.serverx.ServerX;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.UUID;

/**
 * Teleports by running vanilla {@code /execute in <dim> run tp <uuid> ...} as the server.
 * That keeps cross-dimension teleporting working without touching Minecraft internals that
 * change from version to version.
 */
public final class Teleports {

	private Teleports() {
	}

	public static void teleport(MinecraftServer server, UUID player, Location to) {
		String command = String.format(Locale.ROOT, "execute in %s run tp %s %.3f %.3f %.3f %.2f %.2f",
			to.dim, player, to.x, to.y, to.z, to.yaw, to.pitch);
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		} catch (Exception e) {
			ServerX.LOGGER.error("Teleport failed: {}", command, e);
		}
	}
}
