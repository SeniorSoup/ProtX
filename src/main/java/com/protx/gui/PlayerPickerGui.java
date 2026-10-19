package com.protx.gui;

import com.protx.core.Msg;
import com.protx.core.Location;
import com.protx.core.Teleports;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Chest GUI listing online players as heads. Click a head to select it, then click the emerald
 * block to confirm or the barrier to cancel. Used by /bring (teleport them to you) and /goto
 * (teleport you to them).
 */
public final class PlayerPickerGui {

	public enum Mode {
		BRING("Bring a player to you"),
		GOTO("Go to a player");

		final String title;

		Mode(String title) {
			this.title = title;
		}
	}

	private static final int SIZE = 54;
	private static final int PER_PAGE = 45; // rows 0-4 hold heads, row 5 holds the buttons
	private static final int PREV = 45;
	private static final int CANCEL = 48;
	private static final int INFO = 49;
	private static final int CONFIRM = 50;
	private static final int NEXT = 53;

	private PlayerPickerGui() {
	}

	public static void open(MinecraftServer server, ServerPlayer staff, Mode mode) {
		SimpleContainer container = new SimpleContainer(SIZE);
		State state = new State(server, staff, mode, container);
		state.render();
		staff.openMenu(new SimpleMenuProvider(
			(id, inventory, player) -> new GuiMenu(id, inventory, container, state),
			Component.literal(mode.title)));
	}

	private static final class State {
		final MinecraftServer server;
		final ServerPlayer staff;
		final Mode mode;
		final SimpleContainer container;
		final UUID[] slotPlayer = new UUID[PER_PAGE];
		int page;
		UUID selected;

		State(MinecraftServer server, ServerPlayer staff, Mode mode, SimpleContainer container) {
			this.server = server;
			this.staff = staff;
			this.mode = mode;
			this.container = container;
		}

		List<ServerPlayer> candidates() {
			List<ServerPlayer> list = new ArrayList<>();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.getUUID().equals(staff.getUUID())) {
					list.add(player);
				}
			}
			list.sort(Comparator.comparing(p -> p.getScoreboardName().toLowerCase()));
			return list;
		}

		void render() {
			container.clearContent();
			for (int i = 0; i < slotPlayer.length; i++) {
				slotPlayer[i] = null;
			}
			List<ServerPlayer> players = candidates();
			int pages = Math.max(1, (players.size() + PER_PAGE - 1) / PER_PAGE);
			page = Math.max(0, Math.min(page, pages - 1));
			if (selected != null && players.stream().noneMatch(p -> p.getUUID().equals(selected))) {
				selected = null; // they left
			}

			for (int i = 0; i < PER_PAGE; i++) {
				int index = page * PER_PAGE + i;
				if (index >= players.size()) {
					break;
				}
				ServerPlayer player = players.get(index);
				slotPlayer[i] = player.getUUID();
				container.setItem(i, head(player, player.getUUID().equals(selected)));
			}

			ItemStack filler = named(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)), Component.literal(" "));
			for (int slot = PER_PAGE; slot < SIZE; slot++) {
				container.setItem(slot, filler.copy());
			}
			if (players.isEmpty()) {
				container.setItem(22, named(new ItemStack(Items.PAPER), Component.literal("Nobody else is online").withStyle(ChatFormatting.GRAY)));
			}
			if (page > 0) {
				container.setItem(PREV, named(new ItemStack(Items.ARROW), Component.literal("Previous page").withStyle(ChatFormatting.YELLOW)));
			}
			if (page < pages - 1) {
				container.setItem(NEXT, named(new ItemStack(Items.ARROW), Component.literal("Next page").withStyle(ChatFormatting.YELLOW)));
			}
			container.setItem(CANCEL, named(new ItemStack(Items.BARRIER), Component.literal("Cancel").withStyle(ChatFormatting.RED)));

			ServerPlayer chosen = selected == null ? null : server.getPlayerList().getPlayer(selected);
			if (chosen != null) {
				container.setItem(INFO, head(chosen, true));
				container.setItem(CONFIRM, named(new ItemStack(Items.EMERALD_BLOCK), Component.literal(
					(mode == Mode.BRING ? "Confirm: bring " : "Confirm: go to ") + chosen.getScoreboardName()).withStyle(ChatFormatting.GREEN)));
			} else {
				container.setItem(CONFIRM, named(new ItemStack(Items.EMERALD_BLOCK),
					Component.literal("Confirm (select a player first)").withStyle(ChatFormatting.DARK_GREEN)));
			}
		}

		void click(int slot) {
			if (slot >= 0 && slot < PER_PAGE) {
				UUID id = slotPlayer[slot];
				if (id != null) {
					selected = id;
				}
			} else if (slot == PREV && page > 0) {
				page--;
			} else if (slot == NEXT) {
				page++;
			} else if (slot == CANCEL) {
				staff.closeContainer();
				return;
			} else if (slot == CONFIRM) {
				confirm();
				return;
			}
			render();
		}

		void confirm() {
			ServerPlayer target = selected == null ? null : server.getPlayerList().getPlayer(selected);
			if (target == null) {
				Msg.tell(staff, Msg.err(selected == null ? "Select a player first." : "That player is no longer online."));
				render();
				return;
			}
			if (mode == Mode.BRING) {
				Teleports.teleport(server, target.getUUID(), Location.of(staff));
				Msg.tell(staff, Msg.ok("Brought " + target.getScoreboardName() + " to you."));
				Msg.tell(target, Msg.info("You were teleported to " + staff.getScoreboardName() + "."));
			} else {
				Teleports.teleport(server, staff.getUUID(), Location.of(target));
				Msg.tell(staff, Msg.ok("Teleported to " + target.getScoreboardName() + "."));
			}
			staff.closeContainer();
		}
	}

	/** Chest menu whose slots never move items: every click is handled by {@link State#click}. */
	private static final class GuiMenu extends ChestMenu {
		private final State state;

		GuiMenu(int id, Inventory inventory, SimpleContainer container, State state) {
			super(MenuType.GENERIC_9x6, id, inventory, container, 6);
			this.state = state;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput type, Player player) {
			if (type == ContainerInput.PICKUP && slotId >= 0 && slotId < SIZE) {
				state.click(slotId);
			}
			this.sendAllDataToRemote(); // undo whatever the client predicted
		}
	}

	private static ItemStack head(ServerPlayer player, boolean selected) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal((selected ? "✔ " : "") + player.getScoreboardName())
			.withStyle(s -> s.withItalic(false).withColor(selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal(selected ? "Selected - click the emerald block to confirm" : "Click to select")
				.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
		if (selected) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	private static ItemStack named(ItemStack stack, Component name) {
		stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false)));
		return stack;
	}
}
