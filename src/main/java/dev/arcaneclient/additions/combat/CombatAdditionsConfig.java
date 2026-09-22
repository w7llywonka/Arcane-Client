package dev.arcaneclient.additions.combat;

/** Persisted opt-in combat additions. Distances are blocks; health is half-hearts. */
public final class CombatAdditionsConfig {
    public boolean aimAssist = false;
    public int aimFov = 45;
    public int aimRange = 4;
    public int aimDegreesPerTick = 3;
    public boolean autoClicker = false;
    public int clickerCps = 8;
    public boolean clickerIgnoreCooldown = false;
    public boolean autoCrystal = false;
    public int crystalDelayTicks = 6;
    public int crystalMinimumHealth = 16;
    public int crystalMinimumDistance = 3;
    public boolean shieldBreaker = false;
    public boolean maceBomber = false;
    public int maceBomberFallDistance = 3;
    public boolean maceBomberAutoMace = true;
    public boolean maceBomberRestore = true;
    public boolean maceBomberRequireAttack = true;
    public boolean anchorMacro = false;
    public boolean doubleAnchor = false;
    public int anchorDelayTicks = 6;
    public int anchorMinimumHealth = 16;
    public int anchorMinimumDistance = 3;

    public void sanitize() {
        aimFov = Math.clamp(aimFov, 10, 120);
        aimRange = Math.clamp(aimRange, 1, 6);
        aimDegreesPerTick = Math.clamp(aimDegreesPerTick, 1, 10);
        clickerCps = Math.clamp(clickerCps, 1, 12);
        crystalDelayTicks = Math.clamp(crystalDelayTicks, 4, 40);
        crystalMinimumHealth = Math.clamp(crystalMinimumHealth, 8, 36);
        crystalMinimumDistance = Math.clamp(crystalMinimumDistance, 2, 6);
        maceBomberFallDistance = Math.clamp(maceBomberFallDistance, 2, 40);
        anchorDelayTicks = Math.clamp(anchorDelayTicks, 4, 40);
        anchorMinimumHealth = Math.clamp(anchorMinimumHealth, 8, 36);
        anchorMinimumDistance = Math.clamp(anchorMinimumDistance, 2, 6);
    }
}
