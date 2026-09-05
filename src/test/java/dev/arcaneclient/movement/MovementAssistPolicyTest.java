package dev.arcaneclient.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MovementAssistPolicyTest {
    @Test
    void inventoryMovementOnlyRunsInNonTypingContainers() {
        assertTrue(MovementAssistPolicy.inventoryMovement(true, true, false, false));
        assertFalse(MovementAssistPolicy.inventoryMovement(true, true, true, false));
        assertFalse(MovementAssistPolicy.inventoryMovement(true, false, false, false));
        assertFalse(MovementAssistPolicy.inventoryMovement(true, true, false, true));
    }

    @Test
    void safeWalkOnlyCatchesARealGroundEdge() {
        assertTrue(MovementAssistPolicy.safeWalk(true, true, false, false, true, false));
        assertFalse(MovementAssistPolicy.safeWalk(true, true, false, false, true, true));
        assertFalse(MovementAssistPolicy.safeWalk(true, false, false, false, true, false));
        assertFalse(MovementAssistPolicy.safeWalk(true, true, true, false, true, false));
    }

    @Test
    void parkourRequiresAReachableLandingAndCooldown() {
        assertTrue(MovementAssistPolicy.parkourJump(true, true, true, false, true, 0));
        assertFalse(MovementAssistPolicy.parkourJump(true, true, true, false, false, 0));
        assertFalse(MovementAssistPolicy.parkourJump(true, true, true, false, true, 2));
    }
}
