package dev.arcaneclient.additions.utility;

/** Persisted settings for optional utility additions; all automation starts disabled. */
public final class UtilityAdditionsConfig {
    public boolean hoverTotem;
    public int hoverTotemDelayTicks = 4;
    public boolean inventoryTotem;
    public int inventoryTotemDelayTicks = 10;
    public boolean fastUse;
    public int fastUseDelayTicks = 2;
    public boolean spawnerProtect;
    public int spawnerAlertRange = 64;
    public int spawnerAlertCooldownSeconds = 10;

    public void sanitize() {
        hoverTotemDelayTicks = Math.clamp(hoverTotemDelayTicks, 1, 20);
        inventoryTotemDelayTicks = Math.clamp(inventoryTotemDelayTicks, 2, 40);
        fastUseDelayTicks = Math.clamp(fastUseDelayTicks, 1, 4);
        spawnerAlertRange = Math.clamp(spawnerAlertRange, 8, 128);
        spawnerAlertCooldownSeconds = Math.clamp(spawnerAlertCooldownSeconds, 2, 60);
    }
}
