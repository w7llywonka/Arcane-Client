package dev.arcaneclient.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record TunnelSegment(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Type type) {
    public TunnelSegment {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
            throw new IllegalArgumentException("tunnel bounds must have positive size");
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static enum Type {
        TWO_BY_ONE,
        THREE_BY_THREE;

    }
}
