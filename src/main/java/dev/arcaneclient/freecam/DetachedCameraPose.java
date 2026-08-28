package dev.arcaneclient.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;

/** Keeps a non-ticking camera entity's current and previous pose coherent for vanilla interpolation. */
@Environment(EnvType.CLIENT)
public final class DetachedCameraPose {
    private DetachedCameraPose() {
    }

    /** Initializes both interpolation endpoints to the same pose, preventing an activation-frame jump. */
    public static void initialize(Entity camera, double x, double y, double z, float yaw, float pitch) {
        camera.setPosition(x, y, z);
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        camera.resetPosition();
    }

    /** Advances previous position to the old current position before installing the next tick's position. */
    public static void advance(Entity camera, double x, double y, double z) {
        camera.resetPosition();
        camera.setPosition(x, y, z);
    }

    /** Applies mouse rotation without leaving stale angles for Camera.update to interpolate. */
    public static void rotate(Entity camera, float yaw, float pitch) {
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        camera.updateLastAngles();
    }
}
