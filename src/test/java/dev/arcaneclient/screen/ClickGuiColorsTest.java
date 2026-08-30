package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.arcaneclient.ArcaneConfig;
import org.junit.jupiter.api.Test;

final class ClickGuiColorsTest {
    @Test
    void customRgbValuesDriveTheResolvedInterface() {
        ArcaneConfig config = new ArcaneConfig();
        config.customUiColors = true;
        config.uiAccentColor = 0xFF12AB34;
        config.uiPanelColor = 0xFF203040;
        config.uiTextColor = 0xFFEEDDCC;
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        assertEquals(0xFF12AB34, colors.accent());
        assertEquals(0xD8203040, colors.window());
        assertEquals(0xFFEEDDCC, colors.text());
        assertEquals(0x4A, colors.outline() >>> 24);
        assertEquals(0x32, colors.outlineSoft() >>> 24);
        assertNotEquals(colors.row(), colors.window());
    }

    @Test
    void everyPresetThemeIsReachableFromTheConfigAndMonoLeads() {
        assertEquals(ClickGuiTheme.count(), ArcaneConfig.UI_THEME_COUNT);
        assertEquals(ClickGuiTheme.MONO, ClickGuiTheme.fromConfig(0));
        ArcaneConfig config = new ArcaneConfig();
        for (int index = 0; index < ClickGuiTheme.count(); index++) {
            config.uiTheme = index;
            ClickGuiTheme theme = ClickGuiTheme.fromConfig(index);
            ClickGuiColors colors = ClickGuiColors.resolve(config);
            assertEquals(theme.accent(), colors.accent());
            assertEquals(theme.accentAlt(), colors.accentAlt());
        }
    }

    @Test
    void theDefaultThemeCarriesNoHueAtAll() {
        ArcaneConfig config = new ArcaneConfig();
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        int[] palette = {
            colors.accent(), colors.accentAlt(), colors.accentBright(), colors.accentDim(),
            colors.active(), colors.activeHover(), colors.bar(), colors.window(), colors.header(),
            colors.row(), colors.hover(), colors.nest(), colors.outline(), colors.outlineSoft(),
            colors.text(), colors.muted(), colors.faint()
        };
        for (int color : palette) {
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            assertEquals(red, green, () -> "not greyscale: " + Integer.toHexString(color));
            assertEquals(green, blue, () -> "not greyscale: " + Integer.toHexString(color));
        }
    }

    @Test
    void theDefaultThemeGradientsIntoItselfSoNothingSweeps() {
        ClickGuiColors colors = ClickGuiColors.resolve(new ArcaneConfig());
        assertEquals(colors.accent(), colors.accentAlt());
        assertEquals(colors.accent(), RoundedGui.lerp(colors.accent(), colors.accentAlt(), 0.5f));
    }

    @Test
    void customPalettesStillProduceASecondGradientStop() {
        ArcaneConfig config = new ArcaneConfig();
        config.customUiColors = true;
        config.uiAccentColor = 0xFF12AB34;
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        assertNotEquals(colors.accent(), colors.accentAlt());
    }

    @Test
    void rgbChannelEditingPreservesOtherChannels() {
        int edited = ArcaneConfig.withChannel(0xFF102030, 8, 0xAA);
        assertEquals(0xFF10AA30, edited);
        assertEquals(0xAA, ArcaneConfig.channel(edited, 8));
    }
}
