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
        assertEquals(0xE6203040, colors.window());
        assertEquals(0xFFEEDDCC, colors.text());
        assertEquals(0x3D, colors.outline() >>> 24);
        assertEquals(0x24, colors.outlineSoft() >>> 24);
        assertNotEquals(colors.row(), colors.window());
    }

    @Test
    void customRgbChannelsArePreservedWhileSurfaceAlphaRemainsIntentional() {
        ArcaneConfig config = new ArcaneConfig();
        config.customUiColors = true;
        config.uiAccentColor = 0x1212AB34;
        config.uiPanelColor = 0x34203040;
        config.uiTextColor = 0x56EEDDCC;
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        assertEquals(0xFF12AB34, colors.accent());
        assertEquals(0xE6203040, colors.window());
        assertEquals(0xFFEEDDCC, colors.text());
        assertTrue((colors.row() >>> 24) < (colors.window() >>> 24));
        assertTrue((colors.hover() >>> 24) > (colors.row() >>> 24));
    }

    @Test
    void rgbChannelEditingPreservesOtherChannels() {
        int edited = ArcaneConfig.withChannel(0xFF102030, 8, 0xAA);
        assertEquals(0xFF10AA30, edited);
        assertEquals(0xAA, ArcaneConfig.channel(edited, 8));
    }

    @Test
    void themeSelectorOffersSeveralDistinctProfessionalPalettes() {
        assertTrue(ClickGuiTheme.count() >= 6);
        assertNotEquals(ClickGuiTheme.ARCANE.accent(), ClickGuiTheme.AETHER.accent());
        assertNotEquals(ClickGuiTheme.EMBER.accent(), ClickGuiTheme.ULTRAVIOLET.accent());
    }

    @Test
    void everyPresetUsesReadableTextOverTranslucentSurfaces() {
        ArcaneConfig config = new ArcaneConfig();
        for (ClickGuiTheme theme : ClickGuiTheme.values()) {
            config.uiTheme = theme.ordinal();
            ClickGuiColors colors = ClickGuiColors.resolve(config);
            assertEquals(theme.accent(), colors.accent());
            assertEquals(255, colors.text() >>> 24);
            assertTrue((colors.window() >>> 24) >= 192);
            assertTrue((colors.window() >>> 24) < 255);
            assertTrue((colors.outline() >>> 24) < 128);
            assertTrue((colors.row() >>> 24) < (colors.window() >>> 24));
        }
    }

    @Test
    void displayedPaletteAppliesOpacityAndWorldDimConsistently() {
        ArcaneConfig config = new ArcaneConfig();
        config.uiOpacityPercent = 73;
        config.uiBackgroundDimPercent = 18;
        ClickGuiColors colors = ClickGuiColors.display(config);
        assertEquals(UiGeometry.percentAlpha(73), colors.window() >>> 24);
        assertEquals(Math.min(255, UiGeometry.percentAlpha(73) + 10), colors.bar() >>> 24);
        assertEquals(UiGeometry.percentAlpha(18), colors.backdrop() >>> 24);
        assertEquals(0, colors.row() >>> 24);
    }
}
