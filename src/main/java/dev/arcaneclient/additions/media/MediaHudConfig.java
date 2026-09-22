package dev.arcaneclient.additions.media;

/** Opt-in local Spotify metadata display; never stores listening history or credentials. */
public final class MediaHudConfig {
    public boolean enabled;
    /** Bottom left, bottom right, top left, top right. */
    public int anchor;
    public int offsetX = 8;
    public int offsetY = 72;
    public int width = 224;
    public boolean progress = true;
    public boolean hidePaused;

    public void sanitize() {
        anchor = Math.clamp(anchor, 0, 3);
        offsetX = Math.clamp(offsetX, 0, 400);
        offsetY = Math.clamp(offsetY, 0, 400);
        width = Math.clamp(width, 170, 320);
    }
}
