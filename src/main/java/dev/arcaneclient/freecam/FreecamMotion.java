package dev.arcaneclient.freecam;

import net.minecraft.util.math.Vec3d;

/** Pure freecam acceleration/drag math, separated for deterministic testing. */
public final class FreecamMotion {
    private static final double RESPONSE = 0.34;
    private static final double DRAG = 0.72;

    private FreecamMotion() {
    }

    public static Vec3d step(Vec3d velocity, Vec3d target, boolean receivingInput) {
        if (!receivingInput) {
            Vec3d slowed = velocity.multiply(DRAG);
            return slowed.lengthSquared() < 0.000025 ? Vec3d.ZERO : slowed;
        }
        return velocity.multiply(1.0 - RESPONSE).add(target.multiply(RESPONSE));
    }
}
