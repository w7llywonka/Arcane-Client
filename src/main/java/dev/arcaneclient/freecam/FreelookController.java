package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.world.World;

/** Third-person orbit camera anchored to the player without rotating the player's head. */
@Environment(EnvType.CLIENT)
public final class FreelookController {
    private static ArmorStandEntity camera;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;

    private FreelookController() {
    }

    public static boolean isActive() {
        return camera != null;
    }

    public static void toggle(MinecraftClient client) {
        if (isActive()) disable(client); else enable(client);
    }

    public static void enable(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera != null || player == null || client.world == null) return;
        FreecamController.disable(client);
        previousPerspective = client.options.getPerspective();
        camera = new ArmorStandEntity((World) client.world, player.getX(), player.getY(), player.getZ());
        camera.setPosition(player.getX(), player.getEyeY() - camera.getStandingEyeHeight(), player.getZ());
        camera.setYaw(player.getYaw());
        camera.setPitch(Math.clamp(player.getPitch(), -89.9f, 89.9f));
        camera.lastYaw = camera.getYaw();
        camera.lastPitch = camera.getPitch();
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.noClip = true;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        client.setCameraEntity((Entity) camera);
        ArcaneClient.LOGGER.info("Freelook enabled");
    }

    public static void disable(MinecraftClient client) {
        if (camera == null) return;
        if (client.player != null) client.setCameraEntity(client.player); else client.setCameraEntity(null);
        client.options.setPerspective(previousPerspective);
        camera = null;
        ArcaneClient.LOGGER.info("Freelook disabled");
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera == null) return;
        if (player == null || client.world == null || camera.getEntityWorld() != client.world) {
            disable(client);
            return;
        }
        camera.setPosition(player.getX(), player.getEyeY() - camera.getStandingEyeHeight(), player.getZ());
        camera.lastYaw = camera.getYaw();
        camera.lastPitch = camera.getPitch();
    }

    /** Receives vanilla's sensitivity-adjusted mouse deltas and rotates only the orbit camera. */
    public static boolean changeLookDirection(double cursorDeltaX, double cursorDeltaY) {
        if (camera == null) return false;
        CameraRotation.Angles next = CameraRotation.apply(
            camera.getYaw(), camera.getPitch(), cursorDeltaX, cursorDeltaY
        );
        camera.setYaw(next.yaw());
        camera.setPitch(next.pitch());
        camera.lastYaw = next.yaw();
        camera.lastPitch = next.pitch();
        return true;
    }
}
