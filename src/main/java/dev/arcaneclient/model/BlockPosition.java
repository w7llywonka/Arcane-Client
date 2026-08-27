package dev.arcaneclient.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record BlockPosition(int x, int y, int z) {
    public static final int CHUNK_SIZE = 16;

    public int chunkX() {
        return Math.floorDiv(this.x, 16);
    }

    public int chunkZ() {
        return Math.floorDiv(this.z, 16);
    }

    public int localX() {
        return Math.floorMod(this.x, 16);
    }

    public int localZ() {
        return Math.floorMod(this.z, 16);
    }
}
