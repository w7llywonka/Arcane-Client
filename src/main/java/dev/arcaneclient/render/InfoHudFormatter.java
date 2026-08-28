package dev.arcaneclient.render;

import java.util.Locale;

/** Locale-stable compact labels for the client information HUD. */
public final class InfoHudFormatter {
    private InfoHudFormatter() {
    }

    public static String coordinates(double x, double y, double z) {
        return "XYZ  " + (int) Math.floor(x) + "  " + (int) Math.floor(y) + "  " + (int) Math.floor(z);
    }

    public static String speed(double velocityX, double velocityZ) {
        double blocksPerSecond = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ) * 20.0;
        return String.format(Locale.ROOT, "SPEED  %.1f b/s", blocksPerSecond);
    }

    public static String readableId(String path) {
        return path.replace('_', ' ').toUpperCase(Locale.ROOT);
    }
}
