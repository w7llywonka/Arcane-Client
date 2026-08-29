package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Accent palettes drawn over one dense, near-opaque graphite foundation. */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    ARCANE("ARCANE", 0xFF9D8CFF, 0xFFC9C1FF, 0xFF6E62C6, 0xFF2A2450),
    FROST("FROST", 0xFF54A8FF, 0xFFB2D8FF, 0xFF3D77BE, 0xFF16304F),
    ROSE("ROSE", 0xFFF2839C, 0xFFF9B8C6, 0xFFBB6076, 0xFF4A2233);

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final int accent;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int backdrop = 0xA6070910;
    private final int bar = 0xFC0A0D13;
    private final int window = 0xFB0D1017;
    private final int header = 0xFF161B25;
    private final int row = 0xFF1B212C;
    private final int nest = 0xFF06080C;
    private final int outline = 0x74303B4E;
    private final int edge = 0x66000000;
    private final int sheen = 0x24FFFFFF;
    private final int track = 0xFF232A36;
    private final int text = 0xFFF3F4F8;
    private final int muted = 0xFF98A0AE;
    private final int hover;
    private final int activeHover;
    private final int outlineSoft;
    private final int faint;

    ClickGuiTheme(String label, int accent, int accentBright, int accentDim, int active) {
        this.label = label;
        this.accent = accent;
        this.accentBright = accentBright;
        this.accentDim = accentDim;
        this.active = active;
        this.hover = shade(this.row, 1.34f);
        this.activeHover = shade(active, 1.28f);
        this.outlineSoft = withAlpha(shade(this.outline, 0.74f), 0x3C);
        this.faint = shade(this.muted, 0.66f);
    }

    public String label() { return this.label; }
    public int accent() { return this.accent; }
    public int accentBright() { return this.accentBright; }
    public int accentDim() { return this.accentDim; }
    public int active() { return this.active; }
    public int activeHover() { return this.activeHover; }
    public int backdrop() { return this.backdrop; }
    public int bar() { return this.bar; }
    public int window() { return this.window; }
    public int header() { return this.header; }
    public int row() { return this.row; }
    public int hover() { return this.hover; }
    public int nest() { return this.nest; }
    public int outline() { return this.outline; }
    public int outlineSoft() { return this.outlineSoft; }
    public int edge() { return this.edge; }
    public int sheen() { return this.sheen; }
    public int track() { return this.track; }
    public int text() { return this.text; }
    public int muted() { return this.muted; }
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
