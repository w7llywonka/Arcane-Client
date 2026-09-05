package dev.arcaneclient.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Exact vertical weighting used by the archived 1.6/1.8 discovery engine. */
@Environment(EnvType.CLIENT)
public final class DepthProfile {
    private DepthProfile() {
    }

    public static int adjust(int strength, int blockY) {
        int percent = blockY <= 32 ? 130 : (blockY <= 48 ? 100 : (blockY <= 55 ? 55 : 20));
        return Math.max(1, (int)Math.min(Integer.MAX_VALUE, (long)strength * percent / 100L));
    }
}
