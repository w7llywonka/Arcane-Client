package dev.arcaneclient.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class InfoHudFormatterTest {
    @Test
    void formatsNegativeCoordinatesWithFlooring() {
        assertEquals("XYZ  -2  64  9", InfoHudFormatter.coordinates(-1.1, 64.9, 9.99));
    }

    @Test
    void formatsHorizontalSpeedAndReadableIds() {
        assertEquals("SPEED  10.0 b/s", InfoHudFormatter.speed(0.3, 0.4));
        assertEquals("DARK FOREST", InfoHudFormatter.readableId("dark_forest"));
    }
}
