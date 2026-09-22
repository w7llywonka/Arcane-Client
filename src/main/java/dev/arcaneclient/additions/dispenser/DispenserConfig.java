package dev.arcaneclient.additions.dispenser;

/** Explicit, opt-in inventory helper for an already-open vanilla 3x3 container. */
public final class DispenserConfig {
    public boolean enabled;
    public int keepSlot = 1;

    public void sanitize() {
        keepSlot = Math.clamp(keepSlot, 1, 9);
    }
}
