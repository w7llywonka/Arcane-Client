package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiColorTest {
    @Test
    void blendingCarriesAlphaAsWellAsColor() {
        assertEquals(0x00000000, UiColor.blend(0x00000000, 0xFFFFFFFF, 0.0f));
        assertEquals(0xFFFFFFFF, UiColor.blend(0x00000000, 0xFFFFFFFF, 1.0f));
        assertEquals(0x80808080, UiColor.blend(0x00000000, 0xFFFFFFFF, 0.5019608f));
        assertEquals(0xFF102030, UiColor.blend(0xFF102030, 0xFF405060, -1.0f));
    }

    @Test
    void alphaScalingKeepsTheHueAndClampsItsRange() {
        assertEquals(0x80123456, UiColor.scaleAlpha(0xFF123456, 0.5019608f));
        assertEquals(0x00123456, UiColor.scaleAlpha(0xFF123456, -1.0f));
        assertEquals(0xFF123456, UiColor.scaleAlpha(0xFF123456, 4.0f));
    }

    @Test
    void faintSurfacesAreReportedSoTheyCanBeSkipped() {
        assertTrue(UiColor.invisible(UiColor.scaleAlpha(0xFF112233, 0.0f)));
        assertTrue(UiColor.invisible(UiColor.withAlpha(0xFF112233, 2)));
        assertTrue(!UiColor.invisible(UiColor.withAlpha(0xFF112233, 3)));
    }
}
