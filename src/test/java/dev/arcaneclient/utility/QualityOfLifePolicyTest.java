package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QualityOfLifePolicyTest {
    @Test
    void heldAutomationReleasesForScreensWorldLeaveAndDisable() {
        assertTrue(QualityOfLifePolicy.forceHeldInput(true, true, false));
        assertFalse(QualityOfLifePolicy.forceHeldInput(true, true, true));
        assertFalse(QualityOfLifePolicy.forceHeldInput(true, false, false));
        assertFalse(QualityOfLifePolicy.forceHeldInput(false, true, false));
    }

    @Test
    void autoJumpRequiresLiveGroundedMovement() {
        assertTrue(QualityOfLifePolicy.forceJump(true, true, false, true, true));
        assertFalse(QualityOfLifePolicy.forceJump(true, true, false, false, true));
        assertFalse(QualityOfLifePolicy.forceJump(true, true, false, true, false));
        assertFalse(QualityOfLifePolicy.forceJump(true, true, true, true, true));
    }

    @Test
    void clipboardCoordinatesUseBlockFloorsAndGuardUsesRemainingDurability() {
        assertEquals("-2 64 9", QualityOfLifePolicy.coordinates(-1.01, 64.99, 9.0));
        assertTrue(QualityOfLifePolicy.guardDurability(true, 247, 250, 3));
        assertFalse(QualityOfLifePolicy.guardDurability(false, 249, 250, 3));
    }
}
