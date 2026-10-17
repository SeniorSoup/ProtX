package com.serverx.api;

import com.serverx.ServerX;
import com.serverx.core.JsonStore;
import com.serverx.core.PlayerDirectory;
import com.serverx.core.Scheduler;
import com.serverx.core.ServerXConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The surface modules use to talk to ServerX: scheduling, config, player lookup, join/leave
 * hooks and a tiny service registry so modules can expose APIs to each other.
 */
public final class ServerXApi {

	private static final int NOTICE_COOLDOWN_TICKS = 40;

	private final Path dataDir;
	private final Scheduler scheduler = new Scheduler();
	private final PlayerDirectory players;
	private final Map<Class<?>, Object> services = new LinkedHashMap<>();
	private final List<Consumer<ServerPlayer>> joinListeners = new ArrayList<>();
	private final List<Consumer<UUID>> leaveListeners = new ArrayList<>();
	private final Set<UUID> online = new HashSet<>();
	private final Map<UUID, Long> lastNotice = new HashMap<>();

	private ServerXConfig config = new ServerXConfig();
	private MinecraftServer server;

	public ServerXApi(Path dataDir) {
		this.dataDir = dataDir;
		this.players = new PlayerDirectory(dataDir.resolve("players.json"));
	}

	// ---- environment ---------------------------------------------------------------------

	/** The running server. Only valid after the server has started. */
	public MinecraftServer server() {
		if (server == null) {
			throw new IllegalStateException("The server is not running yet");
		}
		return server;
	}

	public boolean hasServer() {
		return server != null;
	}

	/** config/serverx. Put your module's files in here. */
	public Path dataDir() {
		return dataDir;
	}

	public Scheduler scheduler() {
		return scheduler;
	}

	public ServerXConfig config() {
		return config;
	}

	public PlayerDirectory players() {
		return players;
	}

	// ---- services ------------------------------------------------------------------------

	public <T> void registerService(Class<T> type, T implementation) {
		services.put(type, implementation);
	}

	public <T> Optional<T> findService(Class<T> type) {
		return Optional.ofNullable(type.cast(services.get(type)));
	}

	public <T> T service(Class<T> type) {
		return findService(type).orElseThrow(() -> new IllegalStateException("No ServerX service registered for " + type.getName()));
	}

	// ---- join / leave --------------------------------------------------------------------

	/** Called (within about half a second) after a player joins. */
	public void onPlayerJoin(Consumer<ServerPlayer> listener) {
		joinListeners.add(listener);
	}

	/** Called (within about half a second) after a player leaves. */
	public void onPlayerLeave(Consumer<UUID> listener) {
		leaveListeners.add(listener);
	}

	// ---- helpers -------------------------------------------------------------------------

	/** Sends a chat message, but at most about once every two seconds per player (anti-spam). */
	public void notifyThrottled(ServerPlayer player, Component message) {
		long now = scheduler.now();
		Long last = lastNotice.get(player.getUUID());
		if (last != null && now - last < NOTICE_COOLDOWN_TICKS) {
			return;
		}
		lastNotice.put(player.getUUID(), now);
		player.sendSystemMessage(message);
	}

	// ---- internal (used by ServerX) ------------------------------------------------------

	public void attach(MinecraftServer server) {
		this.server = server;
		this.config = JsonStore.load(dataDir.resolve("config.json"), ServerXConfig.class, ServerXConfig::new);
		JsonStore.save(dataDir.resolve("config.json"), config); // writes defaults for new keys
		this.players.load();
	}

	public void detach() {
		players.flush();
		this.server = null;
		online.clear();
		lastNotice.clear();
	}

	public void reloadConfig() {
		this.config = JsonStore.load(dataDir.resolve("config.json"), ServerXConfig.class, ServerXConfig::new);
	}

	/** Polls the player list; runs every few ticks. Version-proof replacement for join/leave events. */
	public void pollPlayers() {
		if (server == null) {
			return;
		}
		Set<UUID> now = new HashSet<>();
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			now.add(player.getUUID());
			if (online.add(player.getUUID())) {
				players.record(player);
				for (Consumer<ServerPlayer> listener : joinListeners) {
					try {
						listener.accept(player);
					} catch (Exception e) {
						ServerX.LOGGER.error("Join listener failed", e);
					}
				}
			}
		}
		for (UUID id : new ArrayList<>(online)) {
			if (!now.contains(id)) {
				online.remove(id);
				for (Consumer<UUID> listener : leaveListeners) {
					try {
						listener.accept(id);
					} catch (Exception e) {
						ServerX.LOGGER.error("Leave listener failed", e);
					}
				}
			}
		}
		players.flush();
	}
}
