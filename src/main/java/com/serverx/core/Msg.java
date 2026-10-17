package com.serverx.core;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Chat message helpers so every ServerX message looks the same. */
public final class Msg {

	private Msg() {
	}

	private static MutableComponent prefixed(String text, ChatFormatting color) {
		return Component.empty()
			.append(Component.literal("[ServerX] ").withStyle(ChatFormatting.GOLD))
			.append(Component.literal(text).withStyle(color));
	}

	public static MutableComponent info(String text) {
		return prefixed(text, ChatFormatting.YELLOW);
	}

	public static MutableComponent ok(String text) {
		return prefixed(text, ChatFormatting.GREEN);
	}

	public static MutableComponent err(String text) {
		return prefixed(text, ChatFormatting.RED);
	}

	/** A line without the prefix, for lists. */
	public static MutableComponent plain(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	public static void tell(ServerPlayer player, Component message) {
		player.sendSystemMessage(message);
	}

	public static void success(CommandSourceStack source, String text) {
		source.sendSuccess(() -> ok(text), false);
	}

	public static void note(CommandSourceStack source, Component component) {
		source.sendSuccess(() -> component, false);
	}

	public static void failure(CommandSourceStack source, String text) {
		source.sendFailure(err(text));
	}
}
