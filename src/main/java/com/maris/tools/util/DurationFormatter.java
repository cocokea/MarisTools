package com.maris.tools.util;

import java.util.concurrent.TimeUnit;

public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String formatSelfDestruct(long millis) {
        long totalSeconds = Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(millis));
        long totalMinutes = totalSeconds / 60L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;
        long seconds = totalSeconds % 60L;
        if (totalSeconds < 60L) {
            return seconds + "s";
        }
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d");
        }
        if (hours > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(hours).append("h");
        }
        if (minutes > 0 || out.isEmpty()) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(minutes).append("m");
        }
        return out.toString();
    }
}
