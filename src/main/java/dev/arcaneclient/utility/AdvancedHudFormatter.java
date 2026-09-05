package dev.arcaneclient.utility;

import java.util.Locale;

/** Locale-stable compact formatting for independent advanced HUD modules. */
public final class AdvancedHudFormatter {
    private AdvancedHudFormatter() {
    }

    public static String duration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remainder = seconds % 60L;
        return hours > 0L
            ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
            : String.format(Locale.ROOT, "%02d:%02d", minutes, remainder);
    }

    public static String distance(double blocks) {
        if (blocks >= 1000.0) return String.format(Locale.ROOT, "%.2f km", blocks / 1000.0);
        return String.format(Locale.ROOT, "%.0f m", blocks);
    }

    public static int durabilityPercent(int damage, int maxDamage) {
        if (maxDamage <= 0) return 100;
        return Math.clamp((maxDamage - damage) * 100 / maxDamage, 0, 100);
    }
}
