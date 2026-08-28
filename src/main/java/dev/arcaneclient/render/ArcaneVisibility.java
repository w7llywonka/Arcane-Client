package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;

/** Shared visibility gate used by Clean Capture across every Arcane overlay. */
public final class ArcaneVisibility {
    private ArcaneVisibility() {
    }

    public static boolean overlaysHidden() {
        return ArcaneClient.config() != null && ArcaneClient.config().cleanCapture;
    }
}
