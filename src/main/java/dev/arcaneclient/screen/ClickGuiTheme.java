package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Palette used by every Click GUI surface. The default theme is the forest/mint system the Arcane
 * product site already uses, so the in-game module windows and the website read as one product.
 */
@Environment(EnvType.CLIENT)
public enum ClickGuiTheme {
    VERDANT(
        "VERDANT",
        0xFF78C796, 0xFFADE0BD, 0xFF4D9D69, 0xFF2F6B49,
        0xE6050B08, 0xF0091209, 0xF00C1710, 0xFF14251A, 0xFF101E16, 0xFF0A1410,
        0xFF213626, 0xFFEDF7F0, 0xFF93AD9B
    ),
    ARCANE(
        "ARCANE",
        0xFFA855F7, 0xFFD8B4FE, 0xFF7E22CE, 0xFF4C2A80,
        0xE6070510, 0xF00B0817, 0xF0100C20, 0xFF1B1533, 0xFF16112A, 0xFF0F0B1D,
        0xFF2E2550, 0xFFF3F0FC, 0xFF9E93C0
    ),
    EMBER(
        "EMBER",
        0xFFFB923C, 0xFFFDBA74, 0xFFC2410C, 0xFF7C3A12,
        0xE6110703, 0xF0170A05, 0xF01C0D07, 0xFF2A160C, 0xFF221109, 0xFF190B05,
        0xFF3D2313, 0xFFFDF4EC, 0xFFC0A08B
    );

    private static final ClickGuiTheme[] VALUES = values();

    private final String label;
    private final int accent;
    private final int accentBright;
    private final int accentDim;
    private final int active;
    private final int backdrop;
    private final int bar;
    private final int window;
    private final int header;
    private final int row;
    private final int nest;
    private final int outline;
    private final int text;
    private final int muted;
    private final int hover;
    private final int activeHover;
    private final int outlineSoft;
    private final int faint;

    ClickGuiTheme(
        String label,
        int accent,
        int accentBright,
        int accentDim,
        int active,
        int backdrop,
        int bar,
        int window,
        int header,
        int row,
        int nest,
        int outline,
        int text,
        int muted
    ) {
        this.label = label;
        this.accent = accent;
        this.accentBright = accentBright;
        this.accentDim = accentDim;
        this.active = active;
        this.backdrop = backdrop;
        this.bar = bar;
        this.window = window;
        this.header = header;
        this.row = row;
        this.nest = nest;
        this.outline = outline;
        this.text = text;
        this.muted = muted;
        this.hover = shade(row, 1.55f);
        this.activeHover = shade(active, 1.22f);
        this.outlineSoft = shade(outline, 0.68f);
        this.faint = shade(muted, 0.66f);
    }

    public String label() {
        return this.label;
    }

    public int accent() {
        return this.accent;
    }

    public int accentBright() {
        return this.accentBright;
    }

    public int accentDim() {
        return this.accentDim;
    }

    public int active() {
        return this.active;
    }

    public int activeHover() {
        return this.activeHover;
    }

    public int backdrop() {
        return this.backdrop;
    }

    public int bar() {
        return this.bar;
    }

    public int window() {
        return this.window;
    }

    public int header() {
        return this.header;
    }

    public int row() {
        return this.row;
    }

    public int hover() {
        return this.hover;
    }

    public int nest() {
        return this.nest;
    }

    public int outline() {
        return this.outline;
    }

    public int outlineSoft() {
        return this.outlineSoft;
    }

    public int text() {
        return this.text;
    }

    public int muted() {
        return this.muted;
    }

    public int faint() {
        return this.faint;
    }

    public ClickGuiTheme next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }

    public static ClickGuiTheme fromConfig(int value) {
        return VALUES[Math.clamp(value, 0, VALUES.length - 1)];
    }

    public static int count() {
        return VALUES.length;
    }

    /** Scales the colour channels of an opaque-or-translucent ARGB value, keeping its alpha. */
    private static int shade(int argb, float factor) {
        int alpha = argb >>> 24;
        int red = Math.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
