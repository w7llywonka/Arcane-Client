package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SafetyRulesTest {
    @Test
    void durabilityGuardBoundaryIsPrecise() {
        assertTrue(SafetyRules.durabilityCritical(97, 100, 3));
        assertFalse(SafetyRules.durabilityCritical(96, 100, 3));
    }

    @Test
    void rangeAndCombinedFlightSafetyUseIndependentSignals() {
        assertTrue(SafetyRules.nearby(64.0 * 64.0, 64));
        assertFalse(SafetyRules.nearby(64.1 * 64.1, 64));
        assertTrue(SafetyRules.flightUnsafe(10, 64, 15, 8));
        assertTrue(SafetyRules.flightUnsafe(90, 4, 15, 8));
        assertFalse(SafetyRules.flightUnsafe(90, 64, 15, 8));
    }

    @Test
    void groupedSafetyThresholdsHonorConfiguredBoundaries() {
        assertTrue(SafetyRules.lowHealth(8.0f, 4));
        assertFalse(SafetyRules.lowHealth(8.1f, 4));
        assertTrue(SafetyRules.lowTotems(2, 2));
        assertFalse(SafetyRules.lowTotems(3, 2));
        assertTrue(SafetyRules.lowArmor(15, 15));
        assertFalse(SafetyRules.lowArmor(16, 15));
    }

    @Test
    void groupedSafetyChoosesPriorityAndIgnoresDisabledRules() {
        assertEquals(
            SafetyRules.Reason.HEALTH,
            SafetyRules.firstTriggered(true, true, true, true, true, true, true, true, true, true)
        );
        assertEquals(
            SafetyRules.Reason.ARMOR,
            SafetyRules.firstTriggered(false, false, true, true, true, true, true, true, true, true)
        );
        assertEquals(
            SafetyRules.Reason.NONE,
            SafetyRules.firstTriggered(false, false, false, false, false, true, true, true, true, true)
        );
    }
}
