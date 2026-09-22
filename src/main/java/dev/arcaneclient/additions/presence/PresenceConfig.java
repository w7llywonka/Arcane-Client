package dev.arcaneclient.additions.presence;

/** This object must never be imported from a shared profile. */
public final class PresenceConfig {
    public boolean enabled;
    public boolean showBadges = true;
    public int badgeColor = 0xFF72CFC6;
    public void sanitize() { badgeColor |= 0xFF000000; }
}
