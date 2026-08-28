package dev.arcaneclient.freecam;

import net.minecraft.util.math.Vec3d;

/** Directional mining ray math: camera direction, player-body origin, real reach distance. */
public final class FreecamMining {
    private FreecamMining() {
    }

    public static Vec3d rayEnd(Vec3d playerEye, float cameraYaw, float cameraPitch, double reach) {
        return playerEye.add(Vec3d.fromPolar(cameraPitch, cameraYaw).multiply(Math.max(0.0, reach)));
    }
}
