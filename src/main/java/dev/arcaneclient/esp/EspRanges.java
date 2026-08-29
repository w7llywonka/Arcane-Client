package dev.arcaneclient.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class EspRanges {
    private EspRanges() {
    }

    public static double horizontalDistanceSquared(double firstX, double firstZ, double secondX, double secondZ) {
        double dx = firstX - secondX;
        double dz = firstZ - secondZ;
        return dx * dx + dz * dz;
    }

    public static boolean withinHorizontalRange(double firstX, double firstZ, double secondX, double secondZ, double range) {
        return horizontalDistanceSquared(firstX, firstZ, secondX, secondZ) <= range * range;
    }
}
