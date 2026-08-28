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
    void sourceTextureKeepsAFourPixelStretchableCenter() {
        assertEquals(4, RoundedGui.SOURCE_CENTER);
        assertEquals(68, RoundedGui.SOURCE_BORDER * 2 + RoundedGui.SOURCE_CENTER);
    }
}