package dev.arcaneclient.screen;

/** ARGB blending helpers shared by the Click GUI and the HUD. */
public final class UiColor {
    private UiColor() {
    }

    /** Blends every channel, alpha included, so a fade can start from a fully transparent colour. */
    public static int blend(int from, int to, float amount) {
        float value = Math.clamp(amount, 0.0f, 1.0f);
        if (value <= 0.0f) return from;
        if (value >= 1.0f) return to;
        int alpha = lerp(from, 24, to, value);
        int red = lerp(from, 16, to, value);
        int green = lerp(from, 8, to, value);
        int blue = lerp(from, 0, to, value);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** Scales a colour's existing alpha, keeping its hue; used for every fade-in surface. */
    public static int scaleAlpha(int color, float scale) {
        int alpha = Math.round((color >>> 24) * Math.clamp(scale, 0.0f, 1.0f));
        return withAlpha(color, alpha);
    }

    public static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24;
    }

    /** True once a surface is too faint to be worth submitting. */
    public static boolean invisible(int color) {
        return (color >>> 24) < 3;
    }

    private static int lerp(int from, int shift, int to, float amount) {
        int start = from >> shift & 0xFF;
        int end = to >> shift & 0xFF;
        return Math.clamp(Math.round(start + (end - start) * amount), 0, 255);
    }
}
