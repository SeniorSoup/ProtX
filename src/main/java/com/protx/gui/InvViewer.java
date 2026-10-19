package com.protx.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * /inv GUI: a 6-row chest that is a live view of another player's inventory.
 *
 * Layout (view slot -> target inventory slot):
 *  row 0: helmet, chestplate, leggings, boots, offhand, then 4 filler panes
 *  rows 1-3: main inventory (inv 9-35)
 *  row 4: hotbar (inv 0-8)
 *  row 5: filler
 */
public final class InvViewer {
	private static final int SIZE = 54;

	private InvViewer() {}

	/** Maps a GUI slot to a slot of the target's Inventory, or -1 for filler. */
	private static int map(int v) {
		return switch (v) {
			case 0 -> 39; // head
			case 1 -> 38; // chest
			case 2 -> 37; // legs
			case 3 -> 36; // feet
			case 4 -> 40; // offhand
			default -> {
				if (v >= 9 && v <= 35) yield v;          // main inventory
				if (v >= 36 && v <= 44) yield v - 36;    // hotbar
				yield -1;
			}
		};
	}

	public static void open(ServerPlayer staff, ServerPlayer target, boolean editable) {
		Container view = new ProxyContainer(target);
		Component title = Component.literal(target.getName().getString() + "'s inventory" + (editable ? "" : " (view only)"));

		staff.openMenu(new SimpleMenuProvider((id, playerInv, p) ->
				new ChestMenu(MenuType.GENERIC_9x6, id, playerInv, view, 6) {
					@Override
					public void clicked(int slotId, int button, ClickType type, Player player) {
						boolean inView = slotId >= 0 && slotId < SIZE;
						boolean filler = inView && map(slotId) < 0;
						boolean modifying = inView
								|| type == ClickType.QUICK_MOVE
								|| type == ClickType.QUICK_CRAFT
								|| type == ClickType.PICKUP_ALL
								|| type == ClickType.SWAP;
						if (filler || (!editable && modifying)) {
							this.sendAllDataToRemote(); // undo the client's prediction
							return;
						}
						super.clicked(slotId, button, type, player);
					}
				}, title));
	}

	/** Container that reads/writes straight through to the target's live inventory. */
	private static final class ProxyContainer implements Container {
		private final ServerPlayer target;
		private final ItemStack filler;

		ProxyContainer(ServerPlayer target) {
			this.target = target;
			this.filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
			this.filler.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(ChatFormatting.GRAY));
		}

		@Override
		public int getContainerSize() {
			return SIZE;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < SIZE; i++) {
				int m = map(i);
				if (m >= 0 && !target.getInventory().getItem(m).isEmpty()) return false;
			}
			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			int m = map(slot);
			return m < 0 ? filler : target.getInventory().getItem(m);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			int m = map(slot);
			return m < 0 ? ItemStack.EMPTY : target.getInventory().removeItem(m, amount);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			int m = map(slot);
			return m < 0 ? ItemStack.EMPTY : target.getInventory().removeItemNoUpdate(m);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			int m = map(slot);
			if (m >= 0) target.getInventory().setItem(m, stack);
		}

		@Override
		public void setChanged() {
			target.getInventory().setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return !target.isRemoved();
		}

		@Override
		public void clearContent() {
			// never wipe a player's inventory through the GUI
		}
	}
}
