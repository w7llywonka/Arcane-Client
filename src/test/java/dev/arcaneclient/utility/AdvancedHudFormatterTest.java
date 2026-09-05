package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AdvancedHudFormatterTest {
    @Test
    void formatsDurationsWithoutLocaleDrift() {
        assertEquals("01:05", AdvancedHudFormatter.duration(65));
        assertEquals("1:01:01", AdvancedHudFormatter.duration(3661));
    }

    @Test
    void formatsDistanceAndDurabilityAtUsefulBoundaries() {
        assertEquals("999 m", AdvancedHudFormatter.distance(999.4));
        assertEquals("1.25 km", AdvancedHudFormatter.distance(1250));
        assertEquals(25, AdvancedHudFormatter.durabilityPercent(75, 100));
        assertEquals(100, AdvancedHudFormatter.durabilityPercent(0, 0));
    }
}
