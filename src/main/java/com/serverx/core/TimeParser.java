package com.serverx.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations like "30s", "10m", "2h", "1d12h", "1w". A bare number means minutes. */
public final class TimeParser {

	private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");
	private static final long MAX_MILLIS = 3650L * 24 * 60 * 60 * 1000; // ten years

	private TimeParser() {
	}

	/** @return the duration in milliseconds, or -1 if the text is not a valid duration. */
	public static long parseMillis(String input) {
		String text = input.trim().toLowerCase(Locale.ROOT);
		try {
			if (text.matches("\\d+")) {
				return clamp(Math.multiplyExact(Long.parseLong(text), 60_000L));
			}
			Matcher matcher = PART.matcher(text);
			int end = 0;
			long total = 0;
			while (matcher.find()) {
				if (matcher.start() != end) {
					return -1;
				}
				end = matcher.end();
				total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), unit(matcher.group(2).charAt(0))));
			}
			if (end != text.length() || total <= 0) {
				return -1;
			}
			return clamp(total);
		} catch (ArithmeticException | NumberFormatException e) {
			return -1;
		}
	}

	private static long unit(char c) {
		return switch (c) {
			case 's' -> 1_000L;
			case 'm' -> 60_000L;
			case 'h' -> 3_600_000L;
			case 'd' -> 86_400_000L;
			default -> 604_800_000L; // 'w'
		};
	}

	private static long clamp(long millis) {
		return Math.min(millis, MAX_MILLIS);
	}

	public static String format(long millis) {
		long seconds = Math.max(0, millis / 1000);
		long days = seconds / 86_400;
		long hours = (seconds % 86_400) / 3_600;
		long minutes = (seconds % 3_600) / 60;
		long secs = seconds % 60;
		StringBuilder out = new StringBuilder();
		if (days > 0) {
			out.append(days).append("d ");
		}
		if (hours > 0) {
			out.append(hours).append("h ");
		}
		if (minutes > 0) {
			out.append(minutes).append("m ");
		}
		if (secs > 0 || out.isEmpty()) {
			out.append(secs).append("s");
		}
		return out.toString().trim();
	}
}
