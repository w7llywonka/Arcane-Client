package dev.arcaneclient.freecam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class FreecamBehaviorTest {
    @Test
    void scrollChangesSpeedOneClampedStepAtATime() {
        assertEquals(9, FreecamSpeed.adjust(8, 1.0));
        assertEquals(7, FreecamSpeed.adjust(8, -1.0));
        assertEquals(75, FreecamSpeed.adjust(75, 1.0));
        assertEquals(1, FreecamSpeed.adjust(1, -1.0));
        assertEquals(75, FreecamSpeed.adjust(500, 0.0));
        assertEquals(7.5, FreecamSpeed.blocksPerTick(75), 0.000001);
    }

    @Test
    void lookingDownMinesBelowTheStationaryPlayerBody() {
        Vec3 eye = new Vec3(10.5, 70.6, -3.5);
        Vec3 end = FreecamMining.rayEnd(eye, 0.0f, 90.0f, 4.5);
        assertEquals(eye.x, end.x, 0.0001);
        assertTrue(end.y < eye.y - 4.4);
        assertEquals(eye.z, end.z, 0.0001);
    }

    @Test
    void motionAcceleratesAndThenDecaysSmoothly() {
        Vec3 velocity = FreecamMotion.step(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), true);
        assertTrue(velocity.x > 0.0 && velocity.x < 1.0);
        Vec3 faster = FreecamMotion.step(velocity, new Vec3(1.0, 0.0, 0.0), true);
        assertTrue(faster.x > velocity.x && faster.x < 1.0);
        Vec3 slowing = FreecamMotion.step(faster, Vec3.ZERO, false);
        assertTrue(slowing.x < faster.x && slowing.x > 0.0);
    }
}
