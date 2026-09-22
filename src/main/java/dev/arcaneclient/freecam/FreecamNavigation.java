package dev.arcaneclient.freecam;

import net.minecraft.world.phys.Vec3;

/** Standard free-flight input: level WASD movement with independent vertical controls. */
public final class FreecamNavigation {
    private FreecamNavigation() {
    }

    public static Vec3 direction(float yaw, double forward, double sideways, double vertical) {
        Vec3 levelForward = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 right = Vec3.directionFromRotation(0.0f, yaw + 90.0f);
        Vec3 direction = levelForward.scale(forward)
            .add(right.scale(sideways))
            .add(0.0, vertical, 0.0);
        return direction.lengthSqr() > 1.0 ? direction.normalize() : direction;
    }
}
