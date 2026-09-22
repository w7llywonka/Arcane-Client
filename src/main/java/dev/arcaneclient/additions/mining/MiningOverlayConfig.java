package dev.arcaneclient.additions.mining;

/** Client-only appearance settings. Enabling this never changes mining behavior. */
public final class MiningOverlayConfig {
    public boolean enabled;
    public int color = 0xFF72CFC6;
    public int fillOpacity = 25;
    public boolean outline = true;
    public int outlineWidth = 2;
    public boolean showPercent;

    public void sanitize() {
        color |= 0xFF000000;
        fillOpacity = Math.clamp(fillOpacity, 0, 70);
        outlineWidth = Math.clamp(outlineWidth, 1, 4);
    }
}
