package com.serverx.modules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.serverx.api.ServerXApi;
import com.serverx.api.ServerXModule;
import com.serverx.core.Location;
import com.serverx.core.Msg;
import com.serverx.core.PlayerDirectory;
import com.serverx.core.Perms;
import com.serverx.core.TimeParser;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /prison, /punishban, /pardonban, /setprison. Prisoners cannot use items, break/place/use blocks,
 * hit entities or run commands, and are pulled back if they wander off or change dimension.
 * Sentences survive restarts and keep running while the player is offline.
 */
public final class PrisonModule implements ServerXModule {

	private ServerXApi api;
	private PrisonService service;

	@Override
	public String id() {
		return "prison";
	}

	// ---- lifecycle -----------------------------------------------------------------------

	@Override
	public void onInit(ServerXApi api) {
		this.api = api;
		this.service = new PrisonService(api);
		api.registerService(PrisonService.class, service);

		api.onPlayerJoin(service::onJoin);
		api.scheduler().repeat(20, () -> {
			if (api.hasServer()) {
				service.tick(api.server());
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> block(player));
		UseItemCallback.EVENT.register((player, level, hand) -> block(player));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> block(player));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> block(player));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> block(player));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && service.isJailed(sp.getUUID())) {
				api.notifyThrottled(sp, Msg.err("You are in prison and cannot do that."));
				return false;
			}
			return true;
		});
	}

	@Override
	public void onEnable(ServerXApi api) {
		service.load();
	}

	@Override
	public void onDisable(ServerXApi api) {
		service.save();
	}

	@Override
	public void onReload(ServerXApi api) {
		service.load();
	}

	private InteractionResult block(Player player) {
		if (player instanceof ServerPlayer sp && service.isJailed(sp.getUUID())) {
			api.notifyThrottled(sp, Msg.err("You are in prison and cannot do that."));
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	// ---- commands ------------------------------------------------------------------------

	@Override
	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ServerXApi api) {
		dispatcher.register(Commands.literal("prison").requires(Perms.PRISON.predicate())
			.then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers)
				.then(Commands.argument("time", StringArgumentType.word())
					.executes(ctx -> prison(ctx, ""))
					.then(Commands.argument("reason", StringArgumentType.greedyString())
						.executes(ctx -> prison(ctx, StringArgumentType.getString(ctx, "reason")))))));

		dispatcher.register(Commands.literal("punishban").requires(Perms.PUNISHBAN.predicate())
			.then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers)
				.executes(ctx -> punishBan(ctx, ""))
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(ctx -> punishBan(ctx, StringArgumentType.getString(ctx, "reason"))))));

		dispatcher.register(Commands.literal("pardonban").requires(Perms.PARDONBAN.predicate())
			.then(Commands.argument("player", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(service.jailedNames(), builder))
				.executes(this::pardon)));

		dispatcher.register(Commands.literal("setprison").requires(Perms.SETPRISON.predicate())
			.executes(this::setPrison));

		// Removes the prison location. Refuses while someone is jailed unless "force" is added.
		dispatcher.register(Commands.literal("rmprison").requires(Perms.RMPRISON.predicate())
			.executes(ctx -> removePrison(ctx, false))
			.then(Commands.literal("force").executes(ctx -> removePrison(ctx, true))));

		dispatcher.register(Commands.literal("prisonlist").requires(Perms.PRISONLIST.predicate())
			.executes(this::prisonList));
	}

	private int removePrison(CommandContext<CommandSourceStack> ctx, boolean force) {
		CommandSourceStack source = ctx.getSource();
		if (service.prison().isEmpty()) {
			Msg.failure(source, "No prison is set.");
			return 0;
		}
		int jailed = service.prisoners().size();
		if (jailed > 0 && !force) {
			Msg.failure(source, jailed + " player(s) are still imprisoned. Use /pardonban on them first, or /rmprison force to release everyone and remove the prison.");
			return 0;
		}
		int released = service.clearPrison(source.getServer());
		Msg.success(source, "Prison removed" + (released > 0 ? " and " + released + " prisoner(s) released." : "."));
		return 1;
	}

	private int prisonList(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<PrisonService.Prisoner> prisoners = service.prisoners();
		Msg.note(source, Msg.info("Prison: " + service.prison().map(Location::describe).orElse("not set")));
		if (prisoners.isEmpty()) {
			Msg.note(source, Msg.plain("Nobody is imprisoned."));
			return 0;
		}
		Msg.note(source, Msg.info("Prisoners (" + prisoners.size() + "):"));
		long now = System.currentTimeMillis();
		for (PrisonService.Prisoner prisoner : prisoners) {
			boolean online = api.server().getPlayerList().getPlayerByName(prisoner.name) != null;
			String sentence = prisoner.permanent ? "permanent" : TimeParser.format(prisoner.releaseAtMs - now) + " left";
			String reason = prisoner.reason == null || prisoner.reason.isBlank() ? "" : " - " + prisoner.reason;
			Msg.note(source, Msg.plain(" - " + prisoner.name + (online ? "" : " (offline)") + ": " + sentence
				+ ", by " + prisoner.by + reason));
		}
		return prisoners.size();
	}

	private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
		CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		List<String> names = new ArrayList<>(api.players().knownNames());
		if (api.hasServer()) {
			for (ServerPlayer player : api.server().getPlayerList().getPlayers()) {
				if (!names.contains(player.getScoreboardName())) {
					names.add(player.getScoreboardName());
				}
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	}

	private int prison(CommandContext<CommandSourceStack> ctx, String reason) {
		String time = StringArgumentType.getString(ctx, "time");
		long millis = TimeParser.parseMillis(time);
		if (millis <= 0) {
			Msg.failure(ctx.getSource(), "'" + time + "' is not a valid time. Use e.g. 30s, 10m, 2h, 1d or 1h30m (a plain number means minutes).");
			return 0;
		}
		return jail(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false, millis, reason);
	}

	private int punishBan(CommandContext<CommandSourceStack> ctx, String reason) {
		return jail(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true, 0, reason);
	}

	private int jail(CommandSourceStack source, String name, boolean permanent, long durationMs, String reason) {
		MinecraftServer server = source.getServer();
		Optional<PlayerDirectory.Target> found = api.players().resolve(server, name);
		if (found.isEmpty()) {
			Msg.failure(source, "Unknown player '" + name + "'. ServerX knows players who have joined since it was installed.");
			return 0;
		}
		PlayerDirectory.Target target = found.get();
		ServerPlayer executor = source.getPlayer();
		if (executor != null && executor.getUUID().equals(target.id())) {
			Msg.failure(source, "You cannot imprison yourself.");
			return 0;
		}
		if (target.online() != null && Perms.PRISON_EXEMPT.has(target.online())) {
			Msg.failure(source, target.name() + " is exempt from prison.");
			return 0;
		}
		PrisonService.Result result = service.jail(server, target, permanent, durationMs, source.getTextName(), reason);
		if (result == PrisonService.Result.NO_PRISON) {
			Msg.failure(source, "No prison is set yet. Stand where you want it and run /setprison.");
			return 0;
		}
		String where = target.online() == null ? " (offline - they will be sent there when they log in)" : "";
		Msg.success(source, target.name() + (permanent ? " is imprisoned permanently" : " is imprisoned for " + TimeParser.format(durationMs)) + where + ".");
		return 1;
	}

	private int pardon(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "player");
		Optional<PlayerDirectory.Target> found = api.players().resolve(source.getServer(), name);
		if (found.isEmpty() || !service.release(source.getServer(), found.get().id())) {
			Msg.failure(source, "'" + name + "' is not in prison.");
			return 0;
		}
		Msg.success(source, found.get().name() + " has been released.");
		return 1;
	}

	private int setPrison(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Location location = Location.of(player);
		service.setPrison(location);
		Msg.success(ctx.getSource(), "Prison set to " + location.describe() + ". Prisoners are kept within "
			+ api.config().prisonLeashRadius + " blocks of it (change prisonLeashRadius in config/serverx/config.json).");
		return 1;
	}
}
