package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ElytraAssistLogicTest {
    @Test
    void fixedIntervalBoostIgnoresCurrentSpeed() {
        assertTrue(ElytraAssistLogic.shouldBoost(true, true, false, 0, 55.0, false, 24));
    }

    @Test
    void smartConservationWaitsUntilFlightSlows() {
        assertFalse(ElytraAssistLogic.shouldBoost(true, true, false, 0, 30.0, true, 24));
        assertTrue(ElytraAssistLogic.shouldBoost(true, true, false, 0, 23.9, true, 24));
    }

    @Test
    void neverBoostsWhenInactiveBlockedGroundedOrCoolingDown() {
        assertFalse(ElytraAssistLogic.shouldBoost(false, true, false, 0, 0.0, false, 24));
        assertFalse(ElytraAssistLogic.shouldBoost(true, false, false, 0, 0.0, false, 24));
        assertFalse(ElytraAssistLogic.shouldBoost(true, true, true, 0, 0.0, false, 24));
        assertFalse(ElytraAssistLogic.shouldBoost(true, true, false, 1, 0.0, false, 24));
    }
}
