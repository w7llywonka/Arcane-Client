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
        assertEquals(0xF0203040, colors.window());
        assertEquals(0xFFEEDDCC, colors.text());
        assertNotEquals(colors.row(), colors.window());
    }

    @Test
    void rgbChannelEditingPreservesOtherChannels() {
        int edited = ArcaneConfig.withChannel(0xFF102030, 8, 0xAA);
        assertEquals(0xFF10AA30, edited);
        assertEquals(0xAA, ArcaneConfig.channel(edited, 8));
    }
}
