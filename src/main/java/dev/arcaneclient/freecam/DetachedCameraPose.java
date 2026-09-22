package dev.arcaneclient.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;

/** Keeps a non-ticking camera entity's current and previous pose coherent for vanilla interpolation. */
@Environment(EnvType.CLIENT)
public final class DetachedCameraPose {
    private DetachedCameraPose() {
    }

    /** Initializes both interpolation endpoints to the same pose, preventing an activation-frame jump. */
    public static void initialize(Entity camera, double x, double y, double z, float yaw, float pitch) {
        camera.setPos(x, y, z);
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.setOldPosAndRot();
    }

    /** Advances previous position to the old current position before installing the next tick's position. */
    public static void advance(Entity camera, double x, double y, double z) {
        camera.setOldPosAndRot();
        camera.setPos(x, y, z);
    }

    /** Applies mouse rotation without leaving stale angles for Camera.update to interpolate. */
    public static void rotate(Entity camera, float yaw, float pitch) {
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.setOldRot();
    }
}
