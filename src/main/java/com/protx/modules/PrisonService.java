package com.protx.modules;

import com.protx.ProtX;
import com.protx.api.ProtXApi;
import com.protx.core.JsonStore;
import com.protx.core.Location;
import com.protx.core.Msg;
import com.protx.core.Perms;
import com.protx.core.PlayerDirectory;
import com.protx.core.Teleports;
import com.protx.core.TimeParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
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
		/** Active IP sentences: ip -> sentence. Everyone joining from that IP gets imprisoned. */
		public Map<String, IpSentence> ipSentences = new LinkedHashMap<>();
		/** Every IP each player (uuid) has been seen on, so alts can be found even while offline. */
		public Map<String, List<String>> playerIps = new LinkedHashMap<>();
	}

	public static final class IpSentence {
		public boolean permanent;
		public long releaseAtMs;
		public String by;
		public String reason;
	}

	public enum Result {
		OK, NO_PRISON
	}

	private final ProtXApi api;
	private final Path file;
	private Data data = new Data();
	/** True once prison.json has been read. Saving before that would overwrite the real file with an empty one. */
	private boolean loaded;

	public PrisonService(ProtXApi api) {
		this.api = api;
		this.file = api.dataDir().resolve("prison.json");
		load(); // do not wait for the server to start: the prison must always be available
	}

	// ---- persistence ---------------------------------------------------------------------

	public void load() {
		data = JsonStore.load(file, Data.class, Data::new);
		if (data.prisoners == null) {
			data.prisoners = new LinkedHashMap<>();
		}
		if (data.ipSentences == null) {
			data.ipSentences = new LinkedHashMap<>();
		}
		if (data.playerIps == null) {
			data.playerIps = new LinkedHashMap<>();
		}
		loaded = true;
		ProtX.LOGGER.info("Prison: {} prisoner(s), prison location {}", data.prisoners.size(),
			data.prison == null ? "not set" : "set");
	}

	public void save() {
		if (!loaded) {
			return; // never overwrite the saved prison with empty data
		}
		JsonStore.save(file, data);
	}

	// ---- queries -------------------------------------------------------------------------

	public Optional<Location> prison() {
		if (data.prison == null && Files.exists(file)) {
			// Not set in memory - double-check the file in case it was loaded too early or edited.
			Data fromDisk = JsonStore.load(file, Data.class, Data::new);
			if (fromDisk.prison != null) {
				data.prison = fromDisk.prison;
			}
		}
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
		if (prison().isEmpty()) {
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


	// ---- IP prison -----------------------------------------------------------------------

	/** Best-effort IP of an online player (no port), or null if unknown. */
	public static String ipOf(ServerPlayer player) {
		try {
			String raw = player.getIpAddress();
			if (raw == null || raw.isBlank()) {
				return null;
			}
			raw = raw.trim();
			if (raw.startsWith("/")) {
				raw = raw.substring(1);
			}
			if (raw.startsWith("[")) { // [ipv6]:port
				int end = raw.indexOf(']');
				return end > 0 ? raw.substring(1, end) : raw;
			}
			int colon = raw.lastIndexOf(':');
			if (colon > 0 && raw.indexOf(':') == colon) { // ipv4:port
				raw = raw.substring(0, colon);
			}
			return raw;
		} catch (Throwable t) {
			return null;
		}
	}

	private void rememberIp(UUID id, String ip) {
		if (ip == null) {
			return;
		}
		List<String> ips = data.playerIps.computeIfAbsent(id.toString(), k -> new ArrayList<>());
		if (!ips.contains(ip)) {
			ips.add(ip);
			save();
		}
	}

	/** Every known player uuid that has ever connected from this IP. */
	public List<UUID> playersOnIp(String ip) {
		List<UUID> result = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : data.playerIps.entrySet()) {
			if (entry.getValue().contains(ip)) {
				result.add(UUID.fromString(entry.getKey()));
			}
		}
		return result;
	}

	public boolean hasIpSentence(String ip) {
		return data.ipSentences.containsKey(ip);
	}

	public Collection<String> ipSentenceIps() {
		return new ArrayList<>(data.ipSentences.keySet());
	}

	/**
	 * Imprisons the target and every other account seen on the same IP, and keeps imprisoning
	 * anyone who later joins from it. Returns the names jailed, or null if there is no prison
	 * or the target's IP is unknown.
	 */
	public IpResult jailIp(MinecraftServer server, PlayerDirectory.Target target, boolean permanent, long durationMs,
						   String by, String reason, java.util.function.Predicate<ServerPlayer> exempt) {
		if (prison().isEmpty()) {
			return new IpResult(Result.NO_PRISON, null, List.of());
		}
		if (target.online() != null) {
			rememberIp(target.id(), ipOf(target.online()));
		}
		List<String> ips = data.playerIps.get(target.id().toString());
		if (ips == null || ips.isEmpty()) {
			return new IpResult(Result.OK, null, List.of());
		}
		String ip = ips.get(ips.size() - 1);
		IpSentence sentence = new IpSentence();
		sentence.permanent = permanent;
		sentence.releaseAtMs = permanent ? 0 : System.currentTimeMillis() + durationMs;
		sentence.by = by;
		sentence.reason = reason;
		data.ipSentences.put(ip, sentence);

		List<String> names = new ArrayList<>();
		for (UUID id : playersOnIp(ip)) {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			if (online != null && exempt.test(online)) {
				continue;
			}
			PlayerDirectory.Target t = new PlayerDirectory.Target(id, api.players().nameOf(id), online);
			jail(server, t, permanent, durationMs, by, reason);
			names.add(t.name());
		}
		save();
		return new IpResult(Result.OK, ip, names);
	}

	public record IpResult(Result result, String ip, List<String> names) {
	}

	/** Removes the IP sentence for the IP this player uses and releases everyone on it. Returns names released. */
	public List<String> pardonIp(MinecraftServer server, UUID id) {
		List<String> released = new ArrayList<>();
		List<String> ips = data.playerIps.get(id.toString());
		if (ips == null) {
			return released;
		}
		for (String ip : ips) {
			if (data.ipSentences.remove(ip) == null) {
				continue;
			}
			for (UUID other : playersOnIp(ip)) {
				String name = data.prisoners.containsKey(other.toString()) ? data.prisoners.get(other.toString()).name : null;
				if (release(server, other) && name != null) {
					released.add(name);
				}
			}
		}
		save();
		return released;
	}

	// ---- hooks ---------------------------------------------------------------------------

	/** A player connected: send jailed players straight back to the prison. */
	public void onJoin(ServerPlayer player) {
		MinecraftServer server = api.server();
		String ip = ipOf(player);
		rememberIp(player.getUUID(), ip);
		if (ip != null && data.prisoners.get(player.getUUID().toString()) == null) {
			IpSentence sentence = data.ipSentences.get(ip);
			if (sentence != null) {
				if (!sentence.permanent && System.currentTimeMillis() >= sentence.releaseAtMs) {
					data.ipSentences.remove(ip);
					save();
				} else if (!Perms.PRISON_EXEMPT.has(player)) {
					long left = sentence.permanent ? 0 : sentence.releaseAtMs - System.currentTimeMillis();
					PlayerDirectory.Target target = new PlayerDirectory.Target(player.getUUID(), player.getScoreboardName(), player);
					jail(server, target, sentence.permanent, left, sentence.by, sentence.reason);
					return;
				}
			}
		}
		Prisoner prisoner = data.prisoners.get(player.getUUID().toString());
		if (prisoner == null) {
			return;
		}
		if (!prisoner.permanent && System.currentTimeMillis() >= prisoner.releaseAtMs) {
			release(server, player.getUUID());
			return;
		}
		if (prison().isEmpty()) {
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
		long now = System.currentTimeMillis();
		if (data.ipSentences.entrySet().removeIf(e -> !e.getValue().permanent && now >= e.getValue().releaseAtMs)) {
			save();
		}
		if (data.prisoners.isEmpty()) {
			return;
		}
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
