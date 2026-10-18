package com.protx.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers every player ProtX has seen (uuid -> last name) so moderation commands also work
 * on players who are offline right now.
 */
public final class PlayerDirectory {

	public static final class Data {
		public Map<String, String> names = new LinkedHashMap<>();
	}

	/** A resolved player. {@code online} is null if they are not connected. */
	public record Target(UUID id, String name, ServerPlayer online) {
	}

	private final Path file;
	private Data data = new Data();
	private boolean dirty;

	public PlayerDirectory(Path file) {
		this.file = file;
	}

	public void load() {
		data = JsonStore.load(file, Data.class, Data::new);
		if (data.names == null) {
			data.names = new LinkedHashMap<>();
		}
	}

	public void record(ServerPlayer player) {
		String name = player.getScoreboardName();
		String previous = data.names.put(player.getUUID().toString(), name);
		if (!name.equals(previous)) {
			dirty = true;
		}
	}

	public void flush() {
		if (dirty) {
			JsonStore.save(file, data);
			dirty = false;
		}
	}

	public Optional<Target> resolve(MinecraftServer server, String name) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			return Optional.of(new Target(online.getUUID(), online.getScoreboardName(), online));
		}
		for (Map.Entry<String, String> entry : data.names.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				UUID id = UUID.fromString(entry.getKey());
				return Optional.of(new Target(id, entry.getValue(), server.getPlayerList().getPlayer(id)));
			}
		}
		return Optional.empty();
	}

	public String nameOf(UUID id) {
		return data.names.getOrDefault(id.toString(), id.toString());
	}

	public Collection<String> knownNames() {
		return new ArrayList<>(data.names.values());
	}
}
