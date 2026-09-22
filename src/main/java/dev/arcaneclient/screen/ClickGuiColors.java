package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneConfig;

/** A resolved Click GUI palette, including fully custom RGB colors. */
public record ClickGuiColors(
    int accent,
    int accentBright,
    int accentDim,
    int active,
    int activeHover,
    int backdrop,
    int bar,
    int window,
    int header,
    int row,
    int hover,
    int nest,
    int outline,
    int outlineSoft,
    int text,
    int muted,
    int faint
) {
    /** Resolves the palette as it is actually displayed, including opacity and world dimming. */
    public static ClickGuiColors display(ArcaneConfig config) {
        ClickGuiColors base = resolve(config);
        int opacity = percentAlpha(config.uiOpacityPercent);
        int strongOpacity = Math.clamp(opacity + 10, 0, 255);
        int softOpacity = Math.min(180, opacity);
        int backdrop = percentAlpha(config.uiBackgroundDimPercent) << 24;
        return new ClickGuiColors(
            base.accent(), base.accentBright(), base.accentDim(), withAlpha(base.active(), 125),
            withAlpha(base.activeHover(), 150), backdrop, withAlpha(base.bar(), strongOpacity),
            withAlpha(base.window(), opacity), withAlpha(base.header(), strongOpacity),
            withAlpha(base.row(), 0), withAlpha(base.hover(), 90), withAlpha(base.nest(), softOpacity),
            base.outline(), base.outlineSoft(), base.text(), base.muted(), base.faint()
        );
    }

    public static ClickGuiColors resolve(ArcaneConfig config) {
        ClickGuiTheme base = ClickGuiTheme.fromConfig(config.uiTheme);
        if (!config.customUiColors) {
            return new ClickGuiColors(
                base.accent(), base.accentBright(), base.accentDim(), base.active(), base.activeHover(),
                base.backdrop(), base.bar(), base.window(), base.header(), base.row(), base.hover(),
                base.nest(), base.outline(), base.outlineSoft(), base.text(), base.muted(), base.faint()
            );
        }

        int accent = opaque(config.uiAccentColor);
        int panel = opaque(config.uiPanelColor);
        int text = opaque(config.uiTextColor);
        int row = withAlpha(mix(panel, text, 0.045f), 0x00);
        int header = withAlpha(mix(panel, text, 0.040f), 0xF2);
        int nest = withAlpha(mix(panel, 0xFF000000, 0.08f), 0xCE);
        int outline = withAlpha(mix(panel, accent, 0.48f), 0x3D);
        int muted = mix(panel, text, 0.61f);
        int active = withAlpha(mix(panel, accent, 0.22f), 0xF2);
        return new ClickGuiColors(
            accent,
            mix(accent, text, 0.34f),
            mix(panel, accent, 0.62f),
            active,
            withAlpha(mix(active, text, 0.07f), 0xF2),
            0x380A070E,
            withAlpha(mix(panel, 0xFF000000, 0.08f), 0xF2),
            withAlpha(panel, 0xE6),
            header,
            row,
            withAlpha(mix(row, text, 0.09f), 0x66),
            nest,
            outline,
            withAlpha(mix(panel, accent, 0.58f), 0x24),
            text,
            muted,
            mix(panel, muted, 0.66f)
        );
    }

    static int mix(int from, int to, float amount) {
        float value = Math.clamp(amount, 0.0f, 1.0f);
        int red = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * value);
        int green = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * value);
        int blue = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * value);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int channel(int color, int shift) {
        return color >> shift & 0xFF;
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | (alpha & 0xFF) << 24;
    }

    private static int percentAlpha(int percent) {
        return Math.clamp(Math.round(255.0f * Math.clamp(percent, 0, 100) / 100.0f), 0, 255);
    }
}
