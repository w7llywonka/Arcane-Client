package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Accent presets over a quiet, neutral foundation. */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    ARCANE("PINK", 0xFFFF3E9C, 0xFFFF82BF, 0xFF922957, 0xF2461A33),
    FROST("BLUE", 0xFF4D9FFF, 0xFF8BC1FF, 0xFF325C92, 0xF21F2F49),
    ROSE("RED", 0xFFFF4D6D, 0xFFFF8BA0, 0xFF922E42, 0xF2461D29),
    EMBER("AMBER", 0xFFFFB84D, 0xFFFFD18B, 0xFF926F32, 0xF2463522),
    AETHER("EMERALD", 0xFF3EE690, 0xFF82EFB7, 0xFF298751, 0xF21C3F31),
    ULTRAVIOLET("PURPLE", 0xFFA85CFF, 0xFFC695FF, 0xFF663692, 0xF2332149);

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final int accent;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int backdrop = 0x380A070E;
    private final int bar = 0xF215181D;
    private final int window = 0xE612151A;
    private final int header = 0xF21B1F25;
    private final int row = 0x00181B20;
    private final int nest = 0xCE101318;
    private final int outline = 0x3D88939E;
    private final int text = 0xFFF0F2F5;
    private final int muted = 0xFFADB4BE;
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
        this.hover = 0x66383E47;
        this.activeHover = shade(active, 1.15f);
        this.outlineSoft = withAlpha(this.outline, 0x24);
        this.faint = 0xFF7E8793;
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
