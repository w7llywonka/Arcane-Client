package dev.arcaneclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SwingDurationTest {
    @Test
    void entireArcaneRangeIsSlowerThanVanilla() {
        assertTrue(SwingDuration.MIN_TICKS > SwingDuration.VANILLA_TICKS);
        assertTrue(SwingDuration.DEFAULT_TICKS > SwingDuration.VANILLA_TICKS);
    }

    @Test
    void clampsToTheSlowOnlyRange() {
        assertEquals(7, SwingDuration.clamp(1));
        assertEquals(12, SwingDuration.clamp(12));
        assertEquals(30, SwingDuration.clamp(99));
    }
}
