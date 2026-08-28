package dev.arcaneclient.freecam;

import net.minecraft.util.math.Vec3d;

/** Standard free-flight input: level WASD movement with independent vertical controls. */
public final class FreecamNavigation {
    private FreecamNavigation() {
    }

    public static Vec3d direction(float yaw, double forward, double sideways, double vertical) {
        Vec3d levelForward = Vec3d.fromPolar(0.0f, yaw);
        Vec3d right = Vec3d.fromPolar(0.0f, yaw + 90.0f);
        Vec3d direction = levelForward.multiply(forward)
            .add(right.multiply(sideways))
            .add(0.0, vertical, 0.0);
        return direction.lengthSquared() > 1.0 ? direction.normalize() : direction;
    }
}
