package com.maris.tools.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhd])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static long parseMillis(String input) {
        if (input == null || input.isBlank()) {
            return -1L;
        }
        String normalized = input.replaceAll("\\s+", "");
        Matcher matcher = PART.matcher(normalized);
        long total = 0L;
        int consumed = 0;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            switch (matcher.group(2).toLowerCase()) {
                case "s" -> total += value * 1000L;
                case "m" -> total += value * 60_000L;
                case "h" -> total += value * 3_600_000L;
                case "d" -> total += value * 86_400_000L;
                default -> {
                }
            }
            consumed += matcher.group(0).length();
        }
        return consumed == normalized.length() && total > 0L ? total : -1L;
    }
}
