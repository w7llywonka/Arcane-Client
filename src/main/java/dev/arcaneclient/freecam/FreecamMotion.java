package dev.arcaneclient.freecam;

import net.minecraft.world.phys.Vec3;

/** Pure freecam acceleration/drag math, separated for deterministic testing. */
public final class FreecamMotion {
    private static final double RESPONSE = 0.34;
    private static final double DRAG = 0.72;

    private FreecamMotion() {
    }

    public static Vec3 step(Vec3 velocity, Vec3 target, boolean receivingInput) {
        if (!receivingInput) {
            Vec3 slowed = velocity.scale(DRAG);
            return slowed.lengthSqr() < 0.000025 ? Vec3.ZERO : slowed;
        }
        return velocity.scale(1.0 - RESPONSE).add(target.scale(RESPONSE));
    }
}
