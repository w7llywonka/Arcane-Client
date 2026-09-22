package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Third-person orbit camera anchored to the player without rotating the player's head. */
@Environment(EnvType.CLIENT)
public final class FreelookController {
    private static boolean active;
    private static CameraType previousPerspective = CameraType.FIRST_PERSON;
    private static float cameraYaw;
    private static float cameraPitch;

    private FreelookController() {
    }

    public static boolean isActive() {
        return active;
    }

    /** True when vanilla should leave the third-person orbit at its full distance through blocks. */
    public static boolean ignoresCameraCollision() {
        return active && ArcaneClient.config().freelookThroughWalls;
    }


    public static void toggle(Minecraft client) {
        if (isActive()) disable(client); else enable(client);
    }

    public static void enable(Minecraft client) {
        LocalPlayer player = client.player;
        if (active || player == null || client.level == null) return;
        FreecamController.disable(client);
        previousPerspective = client.options.getCameraType();
        cameraYaw = player.getYRot();
        cameraPitch = Math.clamp(player.getXRot(), -89.9f, 89.9f);
        active = true;
        DetachedCameraInteraction.stopMining(client);
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        client.setCameraEntity(player);
        ArcaneClient.LOGGER.info("Freelook enabled");
    }

    public static void disable(Minecraft client) {
        if (!active) return;
        active = false;
        DetachedCameraInteraction.stopMining(client);

        if (client.player != null) client.setCameraEntity(client.player); else client.setCameraEntity(null);
        client.options.setCameraType(previousPerspective);
        ArcaneClient.LOGGER.info("Freelook disabled");
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (!active) return;
        if (player == null || client.level == null) {
            disable(client);
            return;
        }
        if (client.getCameraEntity() != player) client.setCameraEntity(player);
        // Freelook only changes the rendered orbit. The real player's look direction
        // remains authoritative for body-origin mining and movement.
        DetachedCameraInteraction.tickMining(
            client,
            player,
            player.getYRot(),
            player.getXRot(),
            true
        );
    }

    /** Receives vanilla's sensitivity-adjusted mouse deltas and rotates only the orbit camera. */
    public static boolean changeLookDirection(double cursorDeltaX, double cursorDeltaY) {
        if (!active) return false;
        CameraRotation.Angles next = CameraRotation.apply(
            cameraYaw, cameraPitch, cursorDeltaX, cursorDeltaY
        );
        cameraYaw = next.yaw();
        cameraPitch = next.pitch();
        return true;
    }

    public static float cameraYaw() {
        return cameraYaw;
    }

    public static float cameraPitch() {
        return cameraPitch;
    }
}
