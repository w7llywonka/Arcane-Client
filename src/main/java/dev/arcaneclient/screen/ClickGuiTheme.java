package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Accent presets over a quiet, neutral foundation. */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    ARCANE("PINK", 0xFFF07CAB, 0xFFF6AAC9, 0xFF86556E, 0xF2332933),
    FROST("BLUE", 0xFF7DADE8, 0xFFA9C8F0, 0xFF516D91, 0xF226303E),
    ROSE("RED", 0xFFEC8494, 0xFFF3AEB8, 0xFF885761, 0xF234292F),
    EMBER("AMBER", 0xFFE5B979, 0xFFEECFA5, 0xFF887554, 0xF2343029),
    AETHER("EMERALD", 0xFF79C9A2, 0xFFA7DDC1, 0xFF4F7F6A, 0xF2243330),
    ULTRAVIOLET("PURPLE", 0xFFB096E6, 0xFFCBB9EF, 0xFF6E5E8D, 0xF22E2A3D);

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final int accent;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int backdrop = 0x38080A10;
    private final int bar = 0xF21A1D25;
    private final int window = 0xE6171A21;
    private final int header = 0xF21E222B;
    private final int row = 0x00171A21;
    private final int nest = 0xCE13161C;
    private final int outline = 0x3D7F8898;
    private final int text = 0xFFE9ECF2;
    private final int muted = 0xFFA8B0BE;
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
        this.hover = 0x66373D49;
        this.activeHover = shade(active, 1.12f);
        this.outlineSoft = withAlpha(this.outline, 0x24);
        this.faint = 0xFF788394;
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
