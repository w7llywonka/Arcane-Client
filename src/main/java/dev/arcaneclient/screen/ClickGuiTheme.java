package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Accent palettes drawn over one neutral graphite foundation. */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    ARCANE("ARCANE", 0xFF9A8CFF, 0xFFC3BCFF, 0xFF6F63C9, 0xFF39345E),
    FROST("FROST", 0xFF76A9FF, 0xFFB8D0FF, 0xFF4E78BD, 0xFF2D4165),
    ROSE("ROSE", 0xFFF08AA0, 0xFFF6B4C1, 0xFFB85D72, 0xFF633440);

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final int accent;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int backdrop = 0x78000000;
    private final int bar = 0xE8101114;
    private final int window = 0xF0121317;
    private final int header = 0xF017181D;
    private final int row = 0xEC1B1C22;
    private final int nest = 0xEC202127;
    private final int outline = 0xFF3A3C45;
    private final int text = 0xFFF4F4F5;
    private final int muted = 0xFFA1A1AA;
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
        this.hover = shade(this.row, 1.55f);
        this.activeHover = shade(active, 1.22f);
        this.outlineSoft = shade(this.outline, 0.68f);
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

    private static int shade(int argb, float factor) {
        int alpha = argb >>> 24;
        int red = Math.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}