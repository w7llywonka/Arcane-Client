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
    int edge,
    int sheen,
    int track,
    int text,
    int muted,
    int faint
) {
    public static ClickGuiColors resolve(ArcaneConfig config) {
        ClickGuiTheme base = ClickGuiTheme.fromConfig(config.uiTheme);
        if (!config.customUiColors) {
            return new ClickGuiColors(
                base.accent(), base.accentBright(), base.accentDim(), base.active(), base.activeHover(),
                base.backdrop(), base.bar(), base.window(), base.header(), base.row(), base.hover(),
                base.nest(), base.outline(), base.outlineSoft(), base.edge(), base.sheen(), base.track(),
                base.text(), base.muted(), base.faint()
            );
        }

        int accent = opaque(config.uiAccentColor);
        int panel = opaque(config.uiPanelColor);
        int text = opaque(config.uiTextColor);
        int row = withAlpha(mix(panel, text, 0.070f), 0xFF);
        int header = withAlpha(mix(panel, text, 0.048f), 0xFF);
        int nest = withAlpha(mix(panel, 0xFF000000, 0.45f), 0xFF);
        int outline = withAlpha(mix(panel, accent, 0.24f), 0x74);
        int muted = mix(panel, text, 0.64f);
        int active = withAlpha(mix(panel, accent, 0.30f), 0xFF);
        return new ClickGuiColors(
            accent,
            mix(accent, text, 0.34f),
            mix(panel, accent, 0.62f),
            active,
            withAlpha(mix(active, text, 0.13f), 0xFF),
            withAlpha(mix(panel, 0xFF000000, 0.62f), 0xA6),
            withAlpha(mix(panel, 0xFF000000, 0.22f), 0xFC),
            withAlpha(panel, 0xFB),
            header,
            row,
            withAlpha(mix(row, text, 0.10f), 0xFF),
            nest,
            outline,
            withAlpha(mix(panel, accent, 0.15f), 0x3C),
            0x66000000,
            withAlpha(text, 0x24),
            withAlpha(mix(panel, text, 0.11f), 0xFF),
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
