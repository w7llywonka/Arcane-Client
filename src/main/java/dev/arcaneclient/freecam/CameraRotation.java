package dev.arcaneclient.freecam;

/** Vanilla-compatible look math shared by Freecam and Freelook. */
public final class CameraRotation {
    static final float VANILLA_LOOK_SCALE = 0.15f;

    private CameraRotation() {
    }

    public static Angles apply(float yaw, float pitch, double cursorDeltaX, double cursorDeltaY) {
        float nextYaw = yaw + (float) cursorDeltaX * VANILLA_LOOK_SCALE;
        float nextPitch = Math.clamp(pitch + (float) cursorDeltaY * VANILLA_LOOK_SCALE, -90.0f, 90.0f);
        return new Angles(nextYaw, nextPitch);
    }

    public record Angles(float yaw, float pitch) {
    }
}
