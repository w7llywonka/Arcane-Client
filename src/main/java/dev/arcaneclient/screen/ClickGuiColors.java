package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneConfig;

/** A resolved Click GUI palette, including fully custom RGB colors. */
public record ClickGuiColors(
    int accent,
    int accentAlt,
    int accentBright,
    int accentDim,
    int active,
    int activeHover,
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
    public static ClickGuiColors resolve(ArcaneConfig config) {
        ClickGuiTheme base = ClickGuiTheme.fromConfig(config.uiTheme);
        if (!config.customUiColors) {
            return new ClickGuiColors(
                base.accent(), base.accentAlt(), base.accentBright(), base.accentDim(), base.active(), base.activeHover(),
                base.bar(), base.window(), base.header(), base.row(), base.hover(),
                base.nest(), base.outline(), base.outlineSoft(), base.text(), base.muted(), base.faint()
            );
        }

        int accent = opaque(config.uiAccentColor);
        int panel = opaque(config.uiPanelColor);
        int text = opaque(config.uiTextColor);
        int row = withAlpha(mix(panel, text, 0.045f), 0xD3);
        int header = withAlpha(mix(panel, text, 0.030f), 0xD9);
        int nest = withAlpha(mix(panel, text, 0.065f), 0xD0);
        int outline = withAlpha(mix(panel, accent, 0.24f), 0x4A);
        int muted = mix(panel, text, 0.64f);
        int active = withAlpha(mix(panel, accent, 0.34f), 0xE6);
        return new ClickGuiColors(
            accent,
            mix(accent, text, 0.52f),
            mix(accent, text, 0.34f),
            mix(panel, accent, 0.62f),
            active,
            withAlpha(mix(active, text, 0.15f), 0xEE),
            withAlpha(mix(panel, 0xFF000000, 0.16f), 0xE8),
            withAlpha(panel, 0xD8),
            header,
            row,
            withAlpha(mix(row, text, 0.09f), 0xDE),
            nest,
            outline,
            withAlpha(mix(panel, accent, 0.15f), 0x32),
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
}
