package com.protx.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.protx.ProtX;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Tiny JSON persistence helper (Gson ships with Minecraft). Use it for your own module data. */
public final class JsonStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonStore() {
	}

	public static <T> T load(Path file, Class<T> type, Supplier<T> fallback) {
		if (!Files.exists(file)) {
			return fallback.get();
		}
		try {
			T value = GSON.fromJson(Files.readString(file), type);
			return value != null ? value : fallback.get();
		} catch (Exception e) {
			ProtX.LOGGER.error("Could not read {} - moving it aside and starting fresh", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
			} catch (IOException ignored) {
				// nothing more we can do
			}
			return fallback.get();
		}
	}

	public static void save(Path file, Object data) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(data));
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			ProtX.LOGGER.error("Could not write {}", file, e);
		}
	}
}
