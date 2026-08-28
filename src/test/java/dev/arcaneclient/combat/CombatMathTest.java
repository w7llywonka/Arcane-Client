package dev.arcaneclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatMathTest {
    @Test
    void durabilityUsesRemainingPercentage() {
        assertEquals(100, CombatMath.durabilityPercent(0, 500));
        assertEquals(25, CombatMath.durabilityPercent(375, 500));
        assertEquals(0, CombatMath.durabilityPercent(500, 500));
    }

    @Test
    void heartThresholdConvertsToHealthPoints() {
        assertTrue(CombatMath.lowHealth(8.0f, 4));
        assertFalse(CombatMath.lowHealth(8.1f, 4));
    }
}
