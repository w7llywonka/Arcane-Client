package dev.arcaneclient.esp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EspRangesTest {
    @Test
    void verticalDepthDoesNotReduceTracerRange() {
        assertTrue(EspRanges.withinHorizontalRange(10.0, 10.0, 10.0, 10.0, 96.0));
        assertTrue(EspRanges.withinHorizontalRange(0.0, 0.0, 60.0, 60.0, 96.0));
        assertFalse(EspRanges.withinHorizontalRange(0.0, 0.0, 97.0, 0.0, 96.0));
    }
}
