package dev.arcaneclient.scan;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class TransientEntityFilter {
    private static final double PLAYER_EXCLUSION_DISTANCE_SQUARED = 144.0;

    private TransientEntityFilter() {
    }

    public static boolean remoteEnoughFromPlayer(double distanceSquared) {
        return distanceSquared > 144.0;
    }
}
