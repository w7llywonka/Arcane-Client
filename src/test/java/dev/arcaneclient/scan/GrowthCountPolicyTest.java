package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GrowthCountPolicyTest {
    @Test void exactCountBoundaryAtEverySliderValue() {
        for (int required = 1; required <= 256; required++) {
            assertTrue(GrowthCountPolicy.score(required - 1, required) < GrowthCountPolicy.FLAG_SCORE);
            assertEquals(GrowthCountPolicy.FLAG_SCORE, GrowthCountPolicy.score(required, required));
            assertTrue(GrowthCountPolicy.score(required + 1, required) >= GrowthCountPolicy.FLAG_SCORE);
        }
    }
    @Test void maximumSensitivityNeedsOneBlockNotEightEvents() {
        var config = new dev.arcaneclient.ArcaneConfig();
        config.setSensitivity(100);
        assertEquals(1, config.grownBlocksRequired);
        assertEquals(100, config.sensitivity());
        assertEquals(50, GrowthCountPolicy.score(1, config.grownBlocksRequired));
        assertEquals(0, GrowthCountPolicy.score(0, config.grownBlocksRequired));
    }
    @Test void densityAndSensitivityAreMonotonicAndBounded() {
        int previous = Integer.MAX_VALUE;
        for (int sensitivity = 0; sensitivity <= 100; sensitivity++) {
            int required = GrowthCountPolicy.fromSensitivity(sensitivity);
            assertTrue(required >= 1 && required <= 256 && required <= previous);
            previous = required;
        }
        assertEquals(100, GrowthCountPolicy.score(Integer.MAX_VALUE, 1));
        assertEquals(0, GrowthCountPolicy.score(-1, 1));
    }
}
