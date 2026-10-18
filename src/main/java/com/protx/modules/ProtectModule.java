package com.protx.modules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.protx.api.ProtXApi;
import com.protx.api.ProtXModule;
import com.protx.core.Location;
import com.protx.core.Msg;
import com.protx.core.Perms;
import com.protx.protlib.Interactables;
import com.protx.protlib.ProtFlag;
import com.protx.protlib.ProtLib;
import com.protx.protlib.Region;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /protect: creates and manages protected regions (built on ProtLib) and enforces them.
 *
 * <p>Inside a region, players without {@code protx.protect.bypass}:
 * cannot break blocks, can place blocks but they decay after the region's decay time (default 2s),
 * can use chests/barrels/doors/trapdoors/buttons/etc., and cannot touch signs.
 * Explosions never destroy blocks in the region. Every rule is a flag you can switch per region.
 */
public final class ProtectModule implements ProtXModule {

	private static final String WAND_TAG = "protx_wand";

	private static final class Session {
		final String name;
		final boolean edit;
		final boolean strict;
		String dim;
		BlockPos p1;
		BlockPos p2;

		Session(String name, boolean edit, boolean strict) {
			this.name = name;
			this.edit = edit;
			this.strict = strict;
		}
	}

	private ProtXApi api;
	private ProtLib lib;
	private final Map<UUID, Session> sessions = new HashMap<>();

	@Override
	public String id() {
		return "protect";
	}

	// ---- lifecycle -----------------------------------------------------------------------

	@Override
	public void onInit(ProtXApi api) {
		this.api = api;
		this.lib = new ProtLib(api);
		api.registerService(ProtLib.class, lib);

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> onUseBlock(player, level, hand, hit));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> onAttackBlock(player, level, hand, pos));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> onBreak(player, level, pos));

		api.scheduler().repeat(8, this::showSelections);
		api.onPlayerLeave(sessions::remove);
		api.onPlayerJoin(player -> {
			if (!sessions.containsKey(player.getUUID())) {
				removeWands(player); // a wand left over from before they disconnected
			}
		});
	}

	@Override
	public void onEnable(ProtXApi api) {
		lib.load();
	}

	@Override
	public void onDisable(ProtXApi api) {
		lib.flushDecays();
		lib.save();
	}

	@Override
	public void onReload(ProtXApi api) {
		lib.load();
	}

	// ---- commands ------------------------------------------------------------------------

	private RequiredArgumentBuilder<CommandSourceStack, String> regionArg() {
		return Commands.argument("name", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(lib.names(), builder));
	}

	@Override
	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, ProtXApi api) {
		dispatcher.register(Commands.literal("protect")
			.executes(this::help)
			.then(Commands.literal("help").requires(Perms.PROTECT_HELP.predicate()).executes(this::help))
			.then(Commands.literal("list").requires(Perms.PROTECT_LIST.predicate()).executes(this::list))
			.then(Commands.literal("info").requires(Perms.PROTECT_LIST.predicate())
				.then(regionArg().executes(this::info)))
			.then(Commands.literal("make").requires(Perms.PROTECT_MAKE.predicate())
				.then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> make(ctx, false))))
			.then(Commands.literal("cancel").requires(Perms.PROTECT_MAKE.predicate()).executes(this::cancel))
			.then(Commands.literal("del").requires(Perms.PROTECT_DEL.predicate())
				.then(regionArg().executes(this::delete)))
			.then(Commands.literal("edit").requires(Perms.PROTECT_EDIT.predicate())
				.then(regionArg()
					.then(Commands.literal("bounds").executes(this::editBounds))
					.then(Commands.literal("flag")
						.then(Commands.argument("flag", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
								lib.flags().stream().map(ProtFlag::id).toList(), builder))
							.then(Commands.argument("value", BoolArgumentType.bool()).executes(this::editFlag))))
					.then(Commands.literal("decay")
						.then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.05, 3600))
							.executes(this::editDecay)))
					.then(Commands.literal("rename")
						.then(Commands.argument("newname", StringArgumentType.word()).executes(this::rename)))))
		);

		// Strict regions: nothing can be placed, broken or used (chests, doors, signs, ... all locked).
		dispatcher.register(Commands.literal("strictprotect").requires(Perms.STRICTPROTECT.predicate())
			.executes(this::strictHelp)
			.then(Commands.literal("make")
				.then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> make(ctx, true))))
			.then(Commands.literal("set")
				.then(regionArg()
					.then(Commands.argument("strict", BoolArgumentType.bool()).executes(this::strictSet))))
		);
	}

	private int strictHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		Msg.note(src, Msg.info("Strict regions: nothing can be placed, broken or used inside."));
		Msg.note(src, Msg.plain("/strictprotect make <name>  - same wand selection as /protect make, but fully locked"));
		Msg.note(src, Msg.plain("/strictprotect set <region> <true|false>  - switch an existing region to/from strict"));
		Msg.note(src, Msg.plain("Everything else (list, info, edit, del) works through /protect."));
		return 1;
	}

	private int strictSet(CommandContext<CommandSourceStack> ctx) {
		Region region = requireRegion(ctx);
		if (region == null) {
			return 0;
		}
		boolean strict = BoolArgumentType.getBool(ctx, "strict");
		applyStrict(region, strict);
		lib.save();
		Msg.success(ctx.getSource(), "'" + region.name + "' is now " + (strict ? "strict (nothing can be placed, broken or used)." : "normal protection."));
		return 1;
	}

	/** Strict = no placing, no interacting, no breaking, signs locked, explosion-proof. */
	private void applyStrict(Region region, boolean strict) {
		region.setFlag(ProtFlag.NO_PLACE, strict);
		region.setFlag(ProtFlag.ALLOW_INTERACT, !strict);
		region.setFlag(ProtFlag.NO_BREAK, true);
		region.setFlag(ProtFlag.LOCK_SIGNS, true);
		region.setFlag(ProtFlag.EXPLOSION_PROOF, true);
	}

	private int help(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		Msg.note(src, Msg.info("Protected regions"));
		String[] lines = {
			"/protect make <name>  - get the wand, left-click corner 1, right-click corner 2",
			"/protect cancel  - abandon the current selection",
			"/protect list  - list all regions",
			"/protect info <name>  - show a region's bounds and flags",
			"/protect edit <name> bounds  - re-select the region's corners with the wand",
			"/protect edit <name> flag <flag> <true|false>  - switch a rule on/off",
			"/protect edit <name> decay <seconds>  - how long placed blocks last",
			"/protect edit <name> rename <newname>",
			"/protect del <name>",
		};
		for (String line : lines) {
			Msg.note(src, Msg.plain(line));
		}
		Msg.note(src, Msg.plain("Flags: " + String.join(", ", lib.flags().stream().map(ProtFlag::id).toList())));
		return 1;
	}

	private int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		if (lib.all().isEmpty()) {
			Msg.success(src, "There are no protected regions yet. Create one with /protect make <name>.");
			return 1;
		}
		Msg.note(src, Msg.info("Protected regions (" + lib.all().size() + "):"));
		for (Region region : lib.all()) {
			Msg.note(src, Msg.plain(" - " + region.name + "  " + region.describeBounds() + "  " + region.dimension));
		}
		return lib.all().size();
	}

	private int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		Region region = requireRegion(ctx);
		if (region == null) {
			return 0;
		}
		Msg.note(src, Msg.info("Region '" + region.name + "'"));
		Msg.note(src, Msg.plain("Bounds: " + region.describeBounds() + " (" + region.volume() + " blocks) in " + region.dimension));
		Msg.note(src, Msg.plain("Decay: " + (region.decayTicks / 20.0) + "s"));
		for (ProtFlag flag : lib.flags()) {
			boolean on = region.flag(flag);
			Msg.note(src, Component.literal("  " + flag.id() + ": " + (on ? "on" : "off") + " - " + flag.description())
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		if (region.createdBy != null) {
			Msg.note(src, Msg.plain("Created by " + region.createdBy));
		}
		return 1;
	}

	private int make(CommandContext<CommandSourceStack> ctx, boolean strict) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "name");
		if (!ProtLib.validName(name)) {
			Msg.failure(ctx.getSource(), "Region names may only contain letters, numbers, '_' and '-' (max 32 characters).");
			return 0;
		}
		if (lib.get(name) != null) {
			Msg.failure(ctx.getSource(), "A region called '" + name + "' already exists.");
			return 0;
		}
		if (!startSession(player, new Session(name, false, strict))) {
			return 0;
		}
		Msg.success(ctx.getSource(), "Selecting " + (strict ? "strict region" : "region") + " '" + name
			+ "': left-click one corner block and right-click the opposite one with the wand. The wand disappears once both corners are set.");
		return 1;
	}

	private int editBounds(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Region region = requireRegion(ctx);
		if (region == null) {
			return 0;
		}
		if (!startSession(player, new Session(region.name, true, false))) {
			return 0;
		}
		Msg.success(ctx.getSource(), "Re-selecting '" + region.name + "': left-click one corner block and right-click the opposite one.");
		return 1;
	}

	private int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		sessions.remove(player.getUUID());
		removeWands(player);
		Msg.success(ctx.getSource(), "Selection cancelled.");
		return 1;
	}

	private int delete(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		if (!lib.delete(name)) {
			Msg.failure(ctx.getSource(), "No region called '" + name + "'.");
			return 0;
		}
		Msg.success(ctx.getSource(), "Deleted region '" + name + "'.");
		return 1;
	}

	private int editFlag(CommandContext<CommandSourceStack> ctx) {
		Region region = requireRegion(ctx);
		if (region == null) {
			return 0;
		}
		String flagId = StringArgumentType.getString(ctx, "flag");
		ProtFlag flag = lib.flag(flagId);
		if (flag == null) {
			Msg.failure(ctx.getSource(), "Unknown flag '" + flagId + "'. Flags: "
				+ String.join(", ", lib.flags().stream().map(ProtFlag::id).toList()));
			return 0;
		}
		boolean value = BoolArgumentType.getBool(ctx, "value");
		region.setFlag(flag, value);
		lib.save();
		Msg.success(ctx.getSource(), "'" + region.name + "': " + flag.id() + " is now " + (value ? "on" : "off") + ".");
		return 1;
	}

	private int editDecay(CommandContext<CommandSourceStack> ctx) {
		Region region = requireRegion(ctx);
		if (region == null) {
			return 0;
		}
		double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
		region.decayTicks = Math.max(1, (int) Math.round(seconds * 20));
		lib.save();
		Msg.success(ctx.getSource(), "'" + region.name + "': placed blocks now last " + (region.decayTicks / 20.0) + "s.");
		return 1;
	}

	private int rename(CommandContext<CommandSourceStack> ctx) {
		Region region = requireRegion(ctx);
		if (region == null) {
			return 0;
		}
		String newName = StringArgumentType.getString(ctx, "newname");
		if (!ProtLib.validName(newName)) {
			Msg.failure(ctx.getSource(), "Region names may only contain letters, numbers, '_' and '-' (max 32 characters).");
			return 0;
		}
		String oldName = region.name;
		if (!lib.rename(oldName, newName)) {
			Msg.failure(ctx.getSource(), "A region called '" + newName + "' already exists.");
			return 0;
		}
		Msg.success(ctx.getSource(), "Renamed '" + oldName + "' to '" + newName + "'.");
		return 1;
	}

	private Region requireRegion(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		Region region = lib.get(name);
		if (region == null) {
			Msg.failure(ctx.getSource(), "No region called '" + name + "'. Try /protect list.");
		}
		return region;
	}

	// ---- wand & selection ----------------------------------------------------------------

	private static ItemStack createWand() {
		ItemStack wand = new ItemStack(Items.GOLDEN_PICKAXE);
		wand.set(DataComponents.CUSTOM_NAME, Component.literal("ProtX Wand").withStyle(ChatFormatting.GOLD));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(WAND_TAG, true);
		wand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		wand.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return wand;
	}

	private static boolean isWand(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(WAND_TAG);
	}

	private static void removeWands(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isWand(inventory.getItem(slot))) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private boolean startSession(ServerPlayer player, Session session) {
		removeWands(player);
		if (!player.getInventory().add(createWand())) {
			Msg.tell(player, Msg.err("Your inventory is full - free a slot for the wand first."));
			return false;
		}
		sessions.put(player.getUUID(), session);
		return true;
	}

	// ---- selection outline ---------------------------------------------------------------

	private static final DustParticleOptions OUTLINE = new DustParticleOptions(0xFFD54A, 0.5f);

	/** Draws the selected cuboid as a faint dotted outline, visible only to the player selecting. */
	private void showSelections() {
		if (sessions.isEmpty() || !api.hasServer()) {
			return;
		}
		for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
			Session session = entry.getValue();
			ServerPlayer player = api.server().getPlayerList().getPlayer(entry.getKey());
			if (player == null || session.p1 == null || !(player.level() instanceof ServerLevel level)
				|| !Location.dimId(level).equals(session.dim)) {
				continue;
			}
			BlockPos other = session.p2 != null ? session.p2 : session.p1;
			drawBox(level, player,
				Math.min(session.p1.getX(), other.getX()), Math.min(session.p1.getY(), other.getY()), Math.min(session.p1.getZ(), other.getZ()),
				Math.max(session.p1.getX(), other.getX()) + 1, Math.max(session.p1.getY(), other.getY()) + 1, Math.max(session.p1.getZ(), other.getZ()) + 1);
		}
	}

	private void drawBox(ServerLevel level, ServerPlayer viewer, double x1, double y1, double z1, double x2, double y2, double z2) {
		double[] xs = {x1, x2};
		double[] ys = {y1, y2};
		double[] zs = {z1, z2};
		double perimeter = 4 * ((x2 - x1) + (y2 - y1) + (z2 - z1));
		double step = Math.max(0.5, perimeter / 300.0); // keep big boxes cheap
		for (double y : ys) {
			for (double z : zs) {
				line(level, viewer, x1, y, z, x2, y, z, step);
			}
		}
		for (double x : xs) {
			for (double z : zs) {
				line(level, viewer, x, y1, z, x, y2, z, step);
			}
		}
		for (double x : xs) {
			for (double y : ys) {
				line(level, viewer, x, y, z1, x, y, z2, step);
			}
		}
	}

	private void line(ServerLevel level, ServerPlayer viewer, double ax, double ay, double az, double bx, double by, double bz, double step) {
		double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
		int points = Math.max(1, (int) Math.ceil(length / step));
		for (int i = 0; i <= points; i++) {
			double t = (double) i / points;
			double x = ax + (bx - ax) * t;
			double y = ay + (by - ay) * t;
			double z = az + (bz - az) * t;
			if (viewer.distanceToSqr(x, y, z) > 64 * 64) {
				continue; // too far to see or worth sending
			}
			level.sendParticles(viewer, OUTLINE, false, false, x, y, z, 1, 0, 0, 0, 0);
		}
	}

	private void wandClick(ServerPlayer player, Level level, BlockPos pos, boolean firstCorner) {
		Session session = sessions.get(player.getUUID());
		if (session == null) {
			Msg.tell(player, Msg.err("No active selection. Start one with /protect make <name>."));
			return;
		}
		String dim = Location.dimId(level);
		if (session.dim != null && !session.dim.equals(dim)) {
			session.p1 = null;
			session.p2 = null; // corners must be in the same dimension
		}
		session.dim = dim;
		BlockPos fixed = pos.immutable();
		if (firstCorner) {
			session.p1 = fixed;
		} else {
			session.p2 = fixed;
		}
		boolean complete = session.p1 != null && session.p2 != null;
		Msg.tell(player, Msg.info((firstCorner ? "Corner 1" : "Corner 2") + " set to (" + fixed.getX() + ", " + fixed.getY()
			+ ", " + fixed.getZ() + ")" + (complete ? "" : " - now pick the other corner.")));
		if (complete) {
			finish(player, session);
		}
	}

	private void finish(ServerPlayer player, Session session) {
		sessions.remove(player.getUUID());
		removeWands(player);
		if (session.edit) {
			Region region = lib.get(session.name);
			if (region == null) {
				Msg.tell(player, Msg.err("Region '" + session.name + "' no longer exists."));
				return;
			}
			region.dimension = session.dim;
			region.setBounds(session.p1, session.p2);
			lib.save();
			Msg.tell(player, Msg.ok("Updated '" + region.name + "': " + region.describeBounds() + " (" + region.volume() + " blocks)."));
			return;
		}
		if (lib.get(session.name) != null) {
			Msg.tell(player, Msg.err("A region called '" + session.name + "' was created in the meantime. Selection discarded."));
			return;
		}
		Region region = lib.create(session.name, session.dim, session.p1, session.p2, player.getScoreboardName());
		if (session.strict) {
			applyStrict(region, true);
			lib.save();
		}
		Msg.tell(player, Msg.ok("Created " + (session.strict ? "strict " : "") + "region '" + region.name + "': " + region.describeBounds() + " (" + region.volume()
			+ " blocks). Tune it with /protect edit " + region.name + " ..."));
	}

	// ---- enforcement ---------------------------------------------------------------------

	private boolean bypass(ServerPlayer player) {
		return Perms.PROTECT_BYPASS.has(player);
	}

	private InteractionResult deny(ServerPlayer player, String message) {
		api.notifyThrottled(player, Msg.err(message));
		return InteractionResult.FAIL;
	}

	private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getItemInHand(hand);
		if (isWand(held)) {
			if (hand == InteractionHand.MAIN_HAND) {
				wandClick(sp, level, hit.getBlockPos(), false);
			}
			return InteractionResult.FAIL;
		}

		BlockPos clicked = hit.getBlockPos();
		BlockState state = level.getBlockState(clicked);
		boolean sneaking = sp.isShiftKeyDown();
		boolean sign = Interactables.isSign(state);
		boolean interactable = Interactables.isInteractable(state);
		boolean placing = held.getItem() instanceof BlockItem && (sneaking || (!sign && !interactable));

		if (!placing) {
			List<Region> atClicked = lib.at(level, clicked);
			if (atClicked.isEmpty() || bypass(sp)) {
				return InteractionResult.PASS;
			}
			if (sign) {
				return ProtLib.anyOn(atClicked, ProtFlag.LOCK_SIGNS) ? deny(sp, "Signs are locked here.") : InteractionResult.PASS;
			}
			if (interactable && (!sneaking || held.isEmpty())) {
				return ProtLib.anyOff(atClicked, ProtFlag.ALLOW_INTERACT) ? deny(sp, "You cannot use that here.") : InteractionResult.PASS;
			}
			if (Interactables.isBuildingTool(held)) {
				return deny(sp, "This area is protected.");
			}
			return InteractionResult.PASS;
		}

		BlockPos placePos = state.canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		List<Region> atPlace = lib.at(level, placePos);
		if (atPlace.isEmpty() || bypass(sp)) {
			return InteractionResult.PASS;
		}
		if (ProtLib.anyOn(atPlace, ProtFlag.NO_PLACE)) {
			return deny(sp, "You cannot place blocks here.");
		}
		int decayTicks = ProtLib.shortestDecay(atPlace);
		if (decayTicks != Integer.MAX_VALUE) {
			lib.trackPlacement(level, placePos, decayTicks, sp.getUUID());
		}
		return InteractionResult.PASS;
	}

	private InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (isWand(sp.getItemInHand(hand))) {
			if (hand == InteractionHand.MAIN_HAND) {
				wandClick(sp, level, pos, true);
			}
			return InteractionResult.FAIL;
		}
		List<Region> at = lib.at(level, pos);
		if (at.isEmpty() || !ProtLib.anyOn(at, ProtFlag.NO_BREAK) || lib.isDecaying(level, pos) || bypass(sp)) {
			return InteractionResult.PASS;
		}
		return deny(sp, "This area is protected.");
	}

	private boolean onBreak(Player player, Level level, BlockPos pos) {
		if (!(player instanceof ServerPlayer sp)) {
			return true;
		}
		List<Region> at = lib.at(level, pos);
		if (at.isEmpty() || !ProtLib.anyOn(at, ProtFlag.NO_BREAK)) {
			return true;
		}
		if (lib.isDecaying(level, pos)) {
			lib.cancelDecay(level, pos); // they broke their own temporary block
			return true;
		}
		if (bypass(sp)) {
			return true;
		}
		api.notifyThrottled(sp, Msg.err("This area is protected."));
		return false;
	}
}
