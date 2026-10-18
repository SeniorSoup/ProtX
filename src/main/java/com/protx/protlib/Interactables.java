package com.protx.protlib;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What counts as "interactable" (usable inside a protected region) and which held items are
 * considered building tools (blocked on non-interactable blocks inside a region).
 * Extensions can add to both lists.
 */
public final class Interactables {

	private static final Set<Block> BLOCKS = new HashSet<>(List.of(
		Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
		Blocks.LEVER, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
		Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER,
		Blocks.ENCHANTING_TABLE, Blocks.BREWING_STAND, Blocks.LECTERN, Blocks.GRINDSTONE,
		Blocks.STONECUTTER, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE,
		Blocks.NOTE_BLOCK, Blocks.JUKEBOX, Blocks.BELL, Blocks.COMPOSTER, Blocks.CAKE,
		Blocks.CHISELED_BOOKSHELF, Blocks.DECORATED_POT, Blocks.REPEATER, Blocks.COMPARATOR,
		Blocks.DAYLIGHT_DETECTOR, Blocks.BEACON
	));

	private static final List<TagKey<Block>> BLOCK_TAGS = new ArrayList<>(List.of(
		BlockTags.DOORS, BlockTags.TRAPDOORS, BlockTags.BUTTONS, BlockTags.FENCE_GATES,
		BlockTags.BEDS, BlockTags.SHULKER_BOXES, BlockTags.ANVIL
	));

	private static final Set<Item> TOOL_ITEMS = new HashSet<>(List.of(
		Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.BONE_MEAL, Items.END_CRYSTAL,
		Items.ARMOR_STAND, Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.PAINTING
	));

	private Interactables() {
	}

	public static void addBlock(Block block) {
		BLOCKS.add(block);
	}

	public static void addBlockTag(TagKey<Block> tag) {
		BLOCK_TAGS.add(tag);
	}

	public static void addToolItem(Item item) {
		TOOL_ITEMS.add(item);
	}

	/** Right-clicking this block uses it (opens a menu, toggles it, ...) instead of building. */
	public static boolean isInteractable(BlockState state) {
		if (BLOCKS.contains(state.getBlock())) {
			return true;
		}
		for (TagKey<Block> tag : BLOCK_TAGS) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isSign(BlockState state) {
		return state.is(BlockTags.ALL_SIGNS);
	}

	/** Held items that change terrain or place things (buckets, axes, fire, bone meal, ...). */
	public static boolean isBuildingTool(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof BucketItem
			|| TOOL_ITEMS.contains(item)
			|| stack.is(ItemTags.AXES)
			|| stack.is(ItemTags.HOES)
			|| stack.is(ItemTags.SHOVELS);
	}
}
