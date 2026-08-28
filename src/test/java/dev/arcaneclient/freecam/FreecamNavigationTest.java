package dev.arcaneclient.freecam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

final class FreecamNavigationTest {
    @Test
    void forwardMovementStaysLevelRegardlessOfCameraPitch() {
        Vec3d direction = FreecamNavigation.direction(37.0f, 1.0, 0.0, 0.0);
        assertEquals(0.0, direction.y, 0.000001);
        assertEquals(1.0, direction.length(), 0.000001);
    }

    @Test
    void verticalMovementIsIndependentAndDiagonalSpeedIsNormalized() {
        Vec3d vertical = FreecamNavigation.direction(0.0f, 0.0, 0.0, 1.0);
        assertEquals(new Vec3d(0.0, 1.0, 0.0), vertical);

        Vec3d diagonal = FreecamNavigation.direction(0.0f, 1.0, 1.0, 1.0);
        assertEquals(1.0, diagonal.length(), 0.000001);
    }
}
