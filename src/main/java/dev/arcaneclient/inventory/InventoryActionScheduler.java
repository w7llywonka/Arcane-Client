package dev.arcaneclient.inventory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small, deterministic lease arbiter for inventory automation.
 *
 * <p>Hotbar selection and inventory clicks are separate resources: changing the
 * selected tool does not need to stall an off-hand swap, while two click based
 * automations must never manipulate the player menu at the same time. Higher
 * priority safety actions may preempt a lower priority lease.</p>
 */
public final class InventoryActionScheduler {
    public enum Channel {
        HOTBAR_SELECTION,
        INVENTORY_CLICK
    }

    public enum Owner {
        AUTO_TOTEM(100),
        HOVER_TOTEM(95),
        SHIELD_BREAKER(88),
        ELYTRA_SWAP(80),
        AIM_ACTION(58),
        CRYSTAL_ACTION(58),
        ANCHOR_ACTION(58),
        INVENTORY_TOTEM(55),
        AUTO_ARMOR(90),
        MACE_SWITCH(85),
        MACE_BOMBER(86),
        DISPENSER_HELPER(30),
        SPEAR_SWITCH(80),
        SMART_WEAPON(75),
        AUTO_MEND(70),
        AUTO_EAT(65),
        HOTBAR_REFILL(60),
        AUTO_TOOL(50),
        ELYTRA_ASSIST(45),
        AUTO_FISH(40);

        private final int priority;

        Owner(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    private record Lease(Owner owner, long expiresAtTick) {
        private boolean activeAt(long tick) {
            return tick < expiresAtTick;
        }
    }

    private static final InventoryActionScheduler SHARED = new InventoryActionScheduler();

    private final Map<Channel, Lease> leases = new EnumMap<>(Channel.class);
    private long latestTick = Long.MIN_VALUE;

    public static InventoryActionScheduler shared() {
        return SHARED;
    }

    /**
     * Attempts to lease a channel for {@code holdTicks}. A hold of one protects
     * the current tick and expires at the beginning of the next tick.
     */
    public synchronized boolean tryAcquire(Owner owner, Channel channel, long tick, int holdTicks) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(channel, "channel");
        if (holdTicks < 1) throw new IllegalArgumentException("holdTicks must be positive");
        observeTick(tick);

        Lease current = leases.get(channel);
        if (current != null && !current.activeAt(tick)) {
            leases.remove(channel);
            current = null;
        }
        if (current == null || current.owner() == owner || owner.priority() > current.owner().priority()) {
            leases.put(channel, new Lease(owner, saturatingAdd(tick, holdTicks)));
            return true;
        }
        return false;
    }

    public synchronized boolean isOwnedBy(Owner owner, Channel channel, long tick) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(channel, "channel");
        observeTick(tick);
        Lease lease = leases.get(channel);
        if (lease == null || !lease.activeAt(tick)) {
            leases.remove(channel);
            return false;
        }
        return lease.owner() == owner;
    }

    public synchronized void release(Owner owner, Channel channel) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(channel, "channel");
        Lease lease = leases.get(channel);
        if (lease != null && lease.owner() == owner) leases.remove(channel);
    }

    public synchronized void releaseAll(Owner owner) {
        Objects.requireNonNull(owner, "owner");
        leases.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
    }

    public synchronized void reset() {
        leases.clear();
        latestTick = Long.MIN_VALUE;
    }

    private void observeTick(long tick) {
        // World changes and test clocks can move backwards. Old leases must not
        // survive that boundary indefinitely.
        if (latestTick != Long.MIN_VALUE && tick < latestTick) leases.clear();
        latestTick = tick;
    }

    private static long saturatingAdd(long value, int increment) {
        if (value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
