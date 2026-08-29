package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Accent palettes drawn over a shared surface foundation. Surfaces are deliberately translucent:
 * the Click GUI dims nothing behind itself, so each panel carries its own legibility.
 */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    /** Pure greyscale, the client's default look. Both accent stops match, so nothing gradients. */
    MONO("MONO", Surface.INK, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF8A8A8A, 0xE62E2E2E),
    KRYPTON("KRYPTON", Surface.MIDNIGHT, 0xFF7B2FFF, 0xFF00FFD1, 0xFF00FFD1, 0xFF5A2BB8, 0xE6241348),
    ARCANE("ARCANE", Surface.GRAPHITE, 0xFFCBFF4A, 0xFF9CFF2E, 0xFFE7FFA6, 0xFF7DA528, 0xE6354818),
    FROST("FROST", Surface.MIDNIGHT, 0xFF3D7DFF, 0xFF63E6FF, 0xFFB8D0FF, 0xFF4E78BD, 0xE62D4165),
    ROSE("ROSE", Surface.GRAPHITE, 0xFFF0567E, 0xFFFFA26B, 0xFFF6B4C1, 0xFFB85D72, 0xE6633440);

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final Surface surface;
    private final int accent;
    private final int accentAlt;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int hover;
    private final int activeHover;
    private final int outlineSoft;
    private final int faint;

    ClickGuiTheme(String label, Surface surface, int accent, int accentAlt, int accentBright, int accentDim, int active) {
        this.label = label;
        this.surface = surface;
        this.accent = accent;
        this.accentAlt = accentAlt;
        this.accentBright = accentBright;
        this.accentDim = accentDim;
        this.active = active;
        this.hover = shade(surface.row(), 1.42f);
        this.activeHover = shade(active, 1.18f);
        this.outlineSoft = withAlpha(surface.outline(), 0x24);
        this.faint = shade(surface.muted(), 0.72f);
    }

    public String label() { return this.label; }
    public int accent() { return this.accent; }
    public int accentAlt() { return this.accentAlt; }
    public int accentBright() { return this.accentBright; }
    public int accentDim() { return this.accentDim; }
    public int active() { return this.active; }
    public int activeHover() { return this.activeHover; }
    public int bar() { return this.surface.bar(); }
    public int window() { return this.surface.window(); }
    public int header() { return this.surface.header(); }
    public int row() { return this.surface.row(); }
    public int hover() { return this.hover; }
    public int nest() { return this.surface.nest(); }
    public int outline() { return this.surface.outline(); }
    public int outlineSoft() { return this.outlineSoft; }
    public int text() { return this.surface.text(); }
    public int muted() { return this.surface.muted(); }
    public int faint() { return this.faint; }

    public ClickGuiTheme next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }

    public static ClickGuiTheme fromConfig(int value) {
        return VALUES[Math.clamp(value, 0, VALUES.length - 1)];
    }

    public static int count() {
        return VALUES.length;
    }

    /** The neutral chrome an accent palette is painted over. */
    private record Surface(int bar, int window, int header, int row, int nest, int outline, int text, int muted) {
        /** Neutral greyscale; every channel matches so the palette carries no hue at all. */
        private static final Surface INK =
            new Surface(0xDC000000, 0xCE060606, 0xC8101010, 0xC0161616, 0xC40A0A0A, 0x4CFFFFFF, 0xFFFFFFFF, 0xFFB0B0B0);
        /** Near-black with a cool blue cast. */
        private static final Surface MIDNIGHT =
            new Surface(0xF0030609, 0xE6070D1A, 0xE00B1424, 0xD40E1729, 0xD20A1220, 0x3A2E5C6E, 0xFFE8F0F8, 0xFF7A9AB5);
        /** Neutral warm-free graphite. */
        private static final Surface GRAPHITE =
            new Surface(0xF00A0C0B, 0xE60E1211, 0xE0161D1A, 0xD41A211E, 0xD2141B18, 0x3A3E4C47, 0xFFF4F4F5, 0xFFA1A1AA);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | (alpha & 0xFF) << 24;
    }

    private static int shade(int argb, float factor) {
        int alpha = argb >>> 24;
        int red = Math.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
