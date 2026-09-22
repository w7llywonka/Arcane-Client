package dev.arcaneclient.additions.susfinder;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SusChunkGeometryTest {
    @Test void observerNearChunkCornerStillDiscoversNearbyDiagonalCenter() {
        assertTrue(SusChunkGeometry.inRange(15, 15, 2, 1, 32));
        assertTrue(SusChunkGeometry.discoveryOffsets(32).contains(new SusChunkGeometry.Offset(2, 1)));
        assertFalse(SusChunkGeometry.inRange(15, 15, 2, 2, 32), "conservative discovery does not expand actual accepted range");
    }

    @Test void discoveryContainsEveryInRangeCenterForPositiveAndNegativeChunkCorners() {
        for (int range : new int[]{32, 33, 512}) {
            var offsets = new HashSet<>(SusChunkGeometry.discoveryOffsets(range));
            int radius = SusChunkGeometry.discoveryRadius(range);
            for (double x : new double[]{-16.0, -0.001, 0, 15.999}) for (double z : new double[]{-16.0, -0.001, 0, 15.999}) {
                int centerX = (int)Math.floor(x / 16), centerZ = (int)Math.floor(z / 16);
                for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                    if (SusChunkGeometry.inRange(x, z, centerX + dx, centerZ + dz, range)) {
                        assertTrue(offsets.contains(new SusChunkGeometry.Offset(dx, dz)), "nearby center was excluded from discovery");
                    }
                }
            }
        }
    }

    @Test void distanceAndDiscoveryAreBoundedAtLargeCoordinates() {
        assertEquals(0, SusChunkGeometry.distanceSquared(-29999992, 29999992, -1875000, 1874999));
        assertEquals(3, SusChunkGeometry.discoveryRadius(-1));
        assertEquals(33, SusChunkGeometry.discoveryRadius(Integer.MAX_VALUE));
    }
}
