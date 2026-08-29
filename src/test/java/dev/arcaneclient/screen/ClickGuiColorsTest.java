package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(0xFB203040, colors.window());
        assertEquals(0xFFEEDDCC, colors.text());
        assertEquals(0x74, colors.outline() >>> 24);
        assertEquals(0x3C, colors.outlineSoft() >>> 24);
        assertNotEquals(colors.row(), colors.window());
    }

    @Test
    void everyPanelSurfaceStaysDenserThanTheWorldBehindIt() {
        for (int theme = 0; theme < ClickGuiTheme.count(); theme++) {
            ArcaneConfig config = new ArcaneConfig();
            config.uiTheme = theme;
            ClickGuiColors colors = ClickGuiColors.resolve(config);
            assertTrue(colors.window() >>> 24 >= 0xF0, "window must read as glass, not a tint");
            assertTrue(colors.bar() >>> 24 >= 0xF0, "bars must read as glass, not a tint");
            assertTrue(colors.row() >>> 24 == 0xFF, "row chips sit on an opaque panel");
            assertTrue(colors.nest() >>> 24 == 0xFF, "nested cards sit on an opaque panel");
            assertTrue(colors.backdrop() >>> 24 > 0x82, "the world dim is denser than the old pass");
        }
    }

    @Test
    void switchTracksAndPanelEdgesResolveForCustomPalettes() {
        ArcaneConfig config = new ArcaneConfig();
        config.customUiColors = true;
        config.uiAccentColor = 0xFF12AB34;
        config.uiPanelColor = 0xFF203040;
        config.uiTextColor = 0xFFEEDDCC;
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        assertEquals(0xFF, colors.track() >>> 24);
        assertNotEquals(colors.track(), colors.window());
        assertEquals(0x24, colors.sheen() >>> 24);
        assertTrue(colors.edge() >>> 24 > 0, "panels need a shadow colour to lift off the world");
    }

    @Test
    void rgbChannelEditingPreservesOtherChannels() {
        int edited = ArcaneConfig.withChannel(0xFF102030, 8, 0xAA);
        assertEquals(0xFF10AA30, edited);
        assertEquals(0xAA, ArcaneConfig.channel(edited, 8));
    }
}
