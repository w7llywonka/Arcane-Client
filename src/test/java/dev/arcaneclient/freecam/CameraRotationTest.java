package dev.arcaneclient.freecam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CameraRotationTest {
    @Test
    void appliesTheExactVanillaLookScale() {
        CameraRotation.Angles angles = CameraRotation.apply(10.0f, -20.0f, 20.0, -40.0);

        assertEquals(13.0f, angles.yaw(), 0.0001f);
        assertEquals(-26.0f, angles.pitch(), 0.0001f);
    }

    @Test
    void clampsPitchAtVanillaLimitsWithoutClampingYaw() {
        CameraRotation.Angles upper = CameraRotation.apply(350.0f, 89.0f, 100.0, 100.0);
        CameraRotation.Angles lower = CameraRotation.apply(-350.0f, -89.0f, -100.0, -100.0);

        assertEquals(365.0f, upper.yaw(), 0.0001f);
        assertEquals(90.0f, upper.pitch(), 0.0001f);
        assertEquals(-365.0f, lower.yaw(), 0.0001f);
        assertEquals(-90.0f, lower.pitch(), 0.0001f);
    }
}
