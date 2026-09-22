package dev.arcaneclient.freecam;

import net.minecraft.world.phys.Vec3;

/** Directional mining ray math: camera direction, player-body origin, real reach distance. */
public final class FreecamMining {
    private FreecamMining() {
    }

    public static Vec3 rayEnd(Vec3 playerEye, float cameraYaw, float cameraPitch, double reach) {
        return playerEye.add(Vec3.directionFromRotation(cameraPitch, cameraYaw).scale(Math.max(0.0, reach)));
    }
}
