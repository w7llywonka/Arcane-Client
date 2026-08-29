package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RoundedGuiTest {
    @Test
    void radiusIsClampedToTheSmallestHalfDimension() {
        assertEquals(7, RoundedGui.effectiveRadius(146, 15, 20));
        assertEquals(5, RoundedGui.effectiveRadius(146, 15, 5));
    }

    @Test
    void invalidOrFlatGeometryFallsBackToASquare() {
        assertEquals(0, RoundedGui.effectiveRadius(100, 20, 0));
        assertEquals(0, RoundedGui.effectiveRadius(0, 20, 8));
    }

    @Test
    void gradientLerpWalksEveryChannelIncludingAlpha() {
        assertEquals(0xFF7B2FFF, RoundedGui.lerp(0xFF7B2FFF, 0xFF00FFD1, 0.0f));
        assertEquals(0xFF00FFD1, RoundedGui.lerp(0xFF7B2FFF, 0xFF00FFD1, 1.0f));
        assertEquals(0x80808080, RoundedGui.lerp(0x00000000, 0xFFFFFFFF, 0.502f));
    }

    @Test
    void gradientLerpClampsOutOfRangeAmounts() {
        assertEquals(0xFF7B2FFF, RoundedGui.lerp(0xFF7B2FFF, 0xFF00FFD1, -3.0f));
        assertEquals(0xFF00FFD1, RoundedGui.lerp(0xFF7B2FFF, 0xFF00FFD1, 4.0f));
    }

    @Test
    void sourceTextureKeepsAFourPixelStretchableCenter() {
        assertEquals(4, RoundedGui.SOURCE_CENTER);
        assertEquals(68, RoundedGui.SOURCE_BORDER * 2 + RoundedGui.SOURCE_CENTER);
    }
}