package dev.arcaneclient.additions.susfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Discovery is conservative about the observer's position inside its chunk; acceptance is exact. */
public final class SusChunkGeometry {
    public record Offset(int x, int z) { }
    private SusChunkGeometry() { }

    public static int discoveryRadius(int range) {
        // Chunk centers can be another sqrt(8^2 + 8^2) blocks from the observer chunk's center.
        return (Math.clamp(range, 32, 512) + 15) / 16 + 1;
    }

    public static List<Offset> discoveryOffsets(int range) {
        int radius = discoveryRadius(range);
        var offsets = new ArrayList<Offset>();
        for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++) {
            if (x * x + z * z <= radius * radius) offsets.add(new Offset(x, z));
        }
        offsets.sort(Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
        return List.copyOf(offsets);
    }

    public static double distanceSquared(double playerX, double playerZ, int chunkX, int chunkZ) {
        double dx = chunkX * 16.0 + 8 - playerX, dz = chunkZ * 16.0 + 8 - playerZ;
        return dx * dx + dz * dz;
    }

    public static boolean inRange(double playerX, double playerZ, int chunkX, int chunkZ, int range) {
        return distanceSquared(playerX, playerZ, chunkX, chunkZ) <= (double)range * range;
    }
}
