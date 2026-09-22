package dev.arcaneclient.additions.cosmetics;

/** Optional fields within the existing Nametags module. */
public final class NametagConfig {
    public boolean players = true;
    public boolean items = true;
    public boolean self;
    public boolean name = true;
    public boolean health = true;
    public boolean distance = true;
    public boolean itemDistance;
    public boolean stackCount = true;
    public boolean armor = true;
    public boolean mainHand = true;
    public boolean offHand = true;
    public int scale = 100;
    public int backgroundOpacity = 56;

    public void sanitize() {
        scale = Math.clamp(scale, 50, 200);
        backgroundOpacity = Math.clamp(backgroundOpacity, 0, 100);
    }

    public int backgroundColor() {
        return Math.round(Math.clamp(backgroundOpacity, 0, 100) * 2.55f) << 24;
    }
}
