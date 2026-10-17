package com.serverx.modules;

import com.serverx.ServerX;
import com.serverx.api.ServerXApi;
import com.serverx.core.JsonStore;
import com.serverx.core.Location;
import com.serverx.core.Msg;
import com.serverx.core.PlayerDirectory;
import com.serverx.core.Teleports;
import com.serverx.core.TimeParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The prison itself: one prison location, a list of prisoners (permanent or timed), and the
 * rules that keep them there. Other modules can use it: {@code api.service(PrisonService.class)}.
 */
public final class PrisonService {

	public static final class Prisoner {
		public String name;
		public boolean permanent;
		public long releaseAtMs;
		public String by;
		public String reason;
		public long since;
		/** Where they were when imprisoned; they are sent back here on release. */
		public Location returnTo;
	}

	public static final class Data {
		public Location prison;
		public Map<String, Prisoner> prisoners = new LinkedHashMap<>();
	}

	public enum Result {
		OK, NO_PRISON
	}

	private final ServerXApi api;
	private final Path file;
	private Data data = new Data();

	public PrisonService(ServerXApi api) {
		this.api = api;
		this.file = api.dataDir().resolve("prison.json");
	}

	// ---- persistence ---------------------------------------------------------------------

	public void load() {
		data = JsonStore.load(file, Data.class, Data::new);
		if (data.prisoners == null) {
			data.prisoners = new LinkedHashMap<>();
		}
		ServerX.LOGGER.info("Prison: {} prisoner(s), prison location {}", data.prisoners.size(),
			data.prison == null ? "not set" : "set");
	}

	public void save() {
		JsonStore.save(file, data);
	}

	// ---- queries -------------------------------------------------------------------------

	public Optional<Location> prison() {
		return Optional.ofNullable(data.prison);
	}

	public void setPrison(Location location) {
		data.prison = location;
		save();
	}

	public boolean isJailed(UUID id) {
		return data.prisoners.containsKey(id.toString());
	}

	public Collection<String> jailedNames() {
		List<String> names = new ArrayList<>();
		for (Prisoner prisoner : data.prisoners.values()) {
			names.add(prisoner.name);
		}
		return names;
	}

	/** Snapshot of everyone currently imprisoned. */
	public List<Prisoner> prisoners() {
		return new ArrayList<>(data.prisoners.values());
	}

	/**
	 * Removes the prison location. Any prisoners are released first (they are sent back to where
	 * they were). Returns how many were released.
	 */
	public int clearPrison(MinecraftServer server) {
		int released = 0;
		for (String key : new ArrayList<>(data.prisoners.keySet())) {
			if (release(server, UUID.fromString(key))) {
				released++;
			}
		}
		data.prison = null;
		save();
		return released;
	}

	// ---- actions -------------------------------------------------------------------------

	public Result jail(MinecraftServer server, PlayerDirectory.Target target, boolean permanent, long durationMs,
					   String by, String reason) {
		if (data.prison == null) {
			return Result.NO_PRISON;
		}
		String key = target.id().toString();
		Prisoner prisoner = data.prisoners.get(key);
		boolean fresh = prisoner == null;
		if (fresh) {
			prisoner = new Prisoner();
			data.prisoners.put(key, prisoner);
		}
		long now = System.currentTimeMillis();
		prisoner.name = target.name();
		prisoner.permanent = permanent;
		prisoner.releaseAtMs = permanent ? 0 : now + durationMs;
		prisoner.by = by;
		prisoner.reason = reason;
		prisoner.since = now;

		ServerPlayer online = target.online();
		if (online != null) {
			if (fresh) {
				prisoner.returnTo = Location.of(online);
			}
			Msg.tell(online, Msg.err(permanent
				? "You have been imprisoned permanently." + reasonSuffix(reason)
				: "You have been imprisoned for " + TimeParser.format(durationMs) + "." + reasonSuffix(reason)));
			Teleports.teleport(server, target.id(), data.prison);
		}
		save();
		return Result.OK;
	}

	public boolean release(MinecraftServer server, UUID id) {
		Prisoner prisoner = data.prisoners.remove(id.toString());
		if (prisoner == null) {
			return false;
		}
		save();
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			if (prisoner.returnTo != null) {
				Teleports.teleport(server, id, prisoner.returnTo);
			}
			Msg.tell(online, Msg.ok("You have been released from prison."));
		}
		return true;
	}

	// ---- hooks ---------------------------------------------------------------------------

	/** A player connected: send jailed players straight back to the prison. */
	public void onJoin(ServerPlayer player) {
		Prisoner prisoner = data.prisoners.get(player.getUUID().toString());
		if (prisoner == null) {
			return;
		}
		MinecraftServer server = api.server();
		if (!prisoner.permanent && System.currentTimeMillis() >= prisoner.releaseAtMs) {
			release(server, player.getUUID());
			return;
		}
		if (data.prison == null) {
			return;
		}
		if (prisoner.returnTo == null) {
			prisoner.returnTo = Location.of(player); // jailed while offline: remember where they logged in
			save();
		}
		Teleports.teleport(server, player.getUUID(), data.prison);
		Msg.tell(player, Msg.err(prisoner.permanent
			? "You are imprisoned permanently." + reasonSuffix(prisoner.reason)
			: "You are imprisoned for another " + TimeParser.format(prisoner.releaseAtMs - System.currentTimeMillis())
				+ "." + reasonSuffix(prisoner.reason)));
	}

	/** Once a second: release finished sentences and pull wanderers back. */
	public void tick(MinecraftServer server) {
		if (data.prisoners.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		int radius = api.config().prisonLeashRadius;
		double radiusSqr = (double) radius * radius;
		for (String key : new ArrayList<>(data.prisoners.keySet())) {
			Prisoner prisoner = data.prisoners.get(key);
			if (prisoner == null) {
				continue;
			}
			UUID id = UUID.fromString(key);
			if (!prisoner.permanent && now >= prisoner.releaseAtMs) {
				release(server, id);
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			if (online == null || data.prison == null) {
				continue;
			}
			boolean wrongDimension = !Location.dimId(online.level()).equals(data.prison.dim);
			if (wrongDimension || data.prison.distanceSqr(online) > radiusSqr) {
				Teleports.teleport(server, id, data.prison);
			}
		}
	}

	private static String reasonSuffix(String reason) {
		return reason == null || reason.isBlank() ? "" : " Reason: " + reason;
	}
}
