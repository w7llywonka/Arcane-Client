package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Accent palettes drawn over one neutral graphite foundation. */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    ARCANE("ARCANE", 0xFF8DBDCE, 0xFFBDE9ED, 0xFF426978, 0xFF202D35),
    FROST("FROST", 0xFF76A9FF, 0xFFB8D0FF, 0xFF4E78BD, 0xE62D4165),
    ROSE("ROSE", 0xFFF08AA0, 0xFFF6B4C1, 0xFFB85D72, 0xE6633440),
    EMBER("EMBER", 0xFFFFA24A, 0xFFFFD1A3, 0xFFB66A27, 0xE6603719),
    AETHER("AETHER", 0xFF45E2D5, 0xFFA8FFF7, 0xFF278E88, 0xE61B5350),
    ULTRAVIOLET("ULTRAVIOLET", 0xFFB594FF, 0xFFD9CAFF, 0xFF7459BA, 0xE640315F);

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final int accent;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int backdrop = 0x70000000;
    private final int bar = 0xFF17191F;
    private final int window = 0xFF17191F;
    private final int header = 0xFF17191F;
    private final int row = 0xFF17191F;
    private final int nest = 0xFF11141A;
    private final int outline = 0xFF414C55;
    private final int text = 0xFFF4F4F5;
    private final int muted = 0xFFB8C0BE;
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
        this.activeHover = shade(active, 1.15f);
        this.outlineSoft = withAlpha(shade(this.outline, 0.72f), 0x28);
        this.faint = shade(this.muted, 0.82f);
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
