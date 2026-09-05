package dev.arcaneclient.movement;

/** Pure decisions used by movement automation and its regression tests. */
public final class MovementAssistPolicy {
    private MovementAssistPolicy() {
    }

    public static boolean inventoryMovement(
        boolean enabled,
        boolean handledScreen,
        boolean textEntryFocused,
        boolean paused
    ) {
        return enabled && handledScreen && !textEntryFocused && !paused;
    }

    public static boolean safeWalk(
        boolean enabled,
        boolean onGround,
        boolean gliding,
        boolean swimming,
        boolean moving,
        boolean supportAhead
    ) {
        return enabled && onGround && !gliding && !swimming && moving && !supportAhead;
    }

    public static boolean parkourJump(
        boolean enabled,
        boolean onGround,
        boolean moving,
        boolean supportAhead,
        boolean landingAhead,
        int cooldownTicks
    ) {
        return enabled && onGround && moving && !supportAhead && landingAhead && cooldownTicks <= 0;
    }

    public static boolean swimAscend(boolean enabled, boolean touchingWater, boolean moving, boolean screenOpen) {
        return enabled && touchingWater && moving && !screenOpen;
    }

    public static boolean vehicleCruise(boolean enabled, boolean controlsVehicle, boolean screenOpen) {
        return enabled && controlsVehicle && !screenOpen;
    }

}
