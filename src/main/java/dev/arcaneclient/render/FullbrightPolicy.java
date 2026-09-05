package dev.arcaneclient.render;

/** Pure decision used by the lightmap hook; it never changes the player's real status effects. */
public final class FullbrightPolicy {
    private FullbrightPolicy() {
    }

    public static boolean lightmapSeesEffect(boolean enabled, boolean checkingNightVision, boolean actualEffect) {
        return actualEffect || enabled && checkingNightVision;
    }
}
