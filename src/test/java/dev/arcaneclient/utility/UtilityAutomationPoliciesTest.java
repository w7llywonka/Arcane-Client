package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import java.util.List;
import org.junit.jupiter.api.Test;

final class UtilityAutomationPoliciesTest {
    @Test
    void hotbarRefillUsesEmptiestAllowedTargetAndLargestMatchingSource() {
        var result = HotbarRefillPolicy.choose(
            List.of(
                new HotbarRefillPolicy.Target(0, 3, 64, true, "rocket"),
                new HotbarRefillPolicy.Target(2, 1, 64, false, "block")
            ),
            List.of(
                new HotbarRefillPolicy.Source(9, 12, "block"),
                new HotbarRefillPolicy.Source(10, 40, "block"),
                new HotbarRefillPolicy.Source(11, 64, "rocket")
            ),
            new HotbarRefillPolicy.Settings(8, false)
        );

        assertTrue(result.isPresent());
        assertEquals(new HotbarRefillPolicy.Operation(10, 2), result.orElseThrow());
    }

    @Test
    void autoFishRequiresSettledWaterAndDownwardThresholdCrossing() {
        AutoFishPolicy policy = new AutoFishPolicy();
        var settings = new AutoFishPolicy.Settings(3, 5, -0.12);

        assertEquals(AutoFishPolicy.Action.CAST, policy.decide(0, true, true, false, false, false, 0.0, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(4, true, true, true, false, false, -0.3, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(5, true, true, true, true, false, -0.2, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(6, true, true, true, true, false, -0.02, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(7, true, true, true, true, false, -0.01, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(8, true, true, true, true, false, -0.03, settings));
        assertEquals(AutoFishPolicy.Action.REEL, policy.decide(9, true, true, true, true, false, -0.2, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(10, true, true, true, true, false, -0.3, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(13, true, true, false, false, false, 0.0, settings));
        assertEquals(AutoFishPolicy.Action.CAST, policy.decide(14, true, true, false, false, false, 0.0, settings));
    }

    @Test
    void autoFishCanReelHookedEntityWithoutWaterArming() {
        AutoFishPolicy policy = new AutoFishPolicy();
        var settings = new AutoFishPolicy.Settings(3, 5, -0.12);

        assertEquals(AutoFishPolicy.Action.CAST, policy.decide(0, true, true, false, false, false, 0.0, settings));
        assertEquals(AutoFishPolicy.Action.NONE, policy.decide(2, true, true, true, false, true, -0.4, settings));
        assertEquals(AutoFishPolicy.Action.REEL, policy.decide(3, true, true, true, false, true, -0.4, settings));
    }

    @Test
    void elytraAssistOwnsBoostTickAgainstAutoFishAndReleasesExplicitly() {
        var scheduler = new InventoryActionScheduler();

        assertTrue(ElytraAssistController.acquireHotbarLease(scheduler, 20));
        assertTrue(scheduler.isOwnedBy(
            InventoryActionScheduler.Owner.ELYTRA_ASSIST,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            20
        ));
        assertFalse(scheduler.tryAcquire(
            InventoryActionScheduler.Owner.AUTO_FISH,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            20,
            1
        ));
        ElytraAssistController.releaseHotbarLease(scheduler);
        assertTrue(scheduler.tryAcquire(
            InventoryActionScheduler.Owner.AUTO_FISH,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            20,
            1
        ));
    }
}
