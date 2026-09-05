package dev.arcaneclient.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.inventory.InventoryActionScheduler.Channel;
import dev.arcaneclient.inventory.InventoryActionScheduler.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InventoryActionSchedulerTest {
    private InventoryActionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InventoryActionScheduler();
    }

    @Test
    void higherPrioritySafetyActionPreemptsLowerPriorityLease() {
        assertTrue(scheduler.tryAcquire(Owner.HOTBAR_REFILL, Channel.INVENTORY_CLICK, 100, 5));
        assertFalse(scheduler.tryAcquire(Owner.AUTO_FISH, Channel.INVENTORY_CLICK, 101, 2));
        assertTrue(scheduler.tryAcquire(Owner.AUTO_TOTEM, Channel.INVENTORY_CLICK, 101, 20));
        assertTrue(scheduler.isOwnedBy(Owner.AUTO_TOTEM, Channel.INVENTORY_CLICK, 101));
        assertFalse(scheduler.isOwnedBy(Owner.HOTBAR_REFILL, Channel.INVENTORY_CLICK, 101));
    }

    @Test
    void equalOwnerRenewsButLowerPriorityWaitsForExpiry() {
        assertTrue(scheduler.tryAcquire(Owner.SMART_WEAPON, Channel.HOTBAR_SELECTION, 30, 2));
        assertTrue(scheduler.tryAcquire(Owner.SMART_WEAPON, Channel.HOTBAR_SELECTION, 31, 4));
        assertFalse(scheduler.tryAcquire(Owner.AUTO_TOOL, Channel.HOTBAR_SELECTION, 34, 1));
        assertTrue(scheduler.tryAcquire(Owner.AUTO_TOOL, Channel.HOTBAR_SELECTION, 35, 1));
    }

    @Test
    void independentChannelsDoNotBlockEachOther() {
        assertTrue(scheduler.tryAcquire(Owner.AUTO_TOTEM, Channel.INVENTORY_CLICK, 7, 10));
        assertTrue(scheduler.tryAcquire(Owner.AUTO_TOOL, Channel.HOTBAR_SELECTION, 7, 2));
    }

    @Test
    void autoEatPreemptsToolsButYieldsToDeliberateWeapons() {
        assertTrue(scheduler.tryAcquire(Owner.AUTO_TOOL, Channel.HOTBAR_SELECTION, 12, 5));
        assertTrue(scheduler.tryAcquire(Owner.AUTO_EAT, Channel.HOTBAR_SELECTION, 12, 5));
        assertFalse(scheduler.tryAcquire(Owner.HOTBAR_REFILL, Channel.HOTBAR_SELECTION, 12, 2));
        assertTrue(scheduler.tryAcquire(Owner.SMART_WEAPON, Channel.HOTBAR_SELECTION, 12, 2));
    }

    @Test
    void elytraBoostPreventsAutoFishInTheSameTickButYieldsToHigherPriorityUse() {
        assertTrue(scheduler.tryAcquire(Owner.ELYTRA_ASSIST, Channel.HOTBAR_SELECTION, 12, 1));
        assertFalse(scheduler.tryAcquire(Owner.AUTO_FISH, Channel.HOTBAR_SELECTION, 12, 1));
        assertTrue(scheduler.tryAcquire(Owner.AUTO_EAT, Channel.HOTBAR_SELECTION, 12, 1));
    }

    @Test
    void backwardsTickClearsLeasesAcrossWorldBoundary() {
        assertTrue(scheduler.tryAcquire(Owner.AUTO_TOTEM, Channel.INVENTORY_CLICK, 400, 20));
        assertTrue(scheduler.tryAcquire(Owner.HOTBAR_REFILL, Channel.INVENTORY_CLICK, 1, 2));
    }
}
