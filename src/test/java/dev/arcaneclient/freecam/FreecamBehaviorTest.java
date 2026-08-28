package dev.arcaneclient.freecam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

final class FreecamBehaviorTest {
    @Test
    void scrollChangesSpeedOneClampedStepAtATime() {
        assertEquals(9, FreecamSpeed.adjust(8, 1.0));
        assertEquals(7, FreecamSpeed.adjust(8, -1.0));
        assertEquals(20, FreecamSpeed.adjust(20, 1.0));
        assertEquals(1, FreecamSpeed.adjust(1, -1.0));
    }

    @Test
    void lookingDownMinesBelowTheStationaryPlayerBody() {
        Vec3d eye = new Vec3d(10.5, 70.6, -3.5);
        Vec3d end = FreecamMining.rayEnd(eye, 0.0f, 90.0f, 4.5);
        assertEquals(eye.x, end.x, 0.0001);
        assertTrue(end.y < eye.y - 4.4);
        assertEquals(eye.z, end.z, 0.0001);
    }

    @Test
    void motionAcceleratesAndThenDecaysSmoothly() {
        Vec3d velocity = FreecamMotion.step(Vec3d.ZERO, new Vec3d(1.0, 0.0, 0.0), true);
        assertTrue(velocity.x > 0.0 && velocity.x < 1.0);
        Vec3d faster = FreecamMotion.step(velocity, new Vec3d(1.0, 0.0, 0.0), true);
        assertTrue(faster.x > velocity.x && faster.x < 1.0);
        Vec3d slowing = FreecamMotion.step(faster, Vec3d.ZERO, false);
        assertTrue(slowing.x < faster.x && slowing.x > 0.0);
    }
}
