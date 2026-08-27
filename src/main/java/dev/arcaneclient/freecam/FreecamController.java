package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Environment(value=EnvType.CLIENT)
public final class FreecamController {
    private static ArmorStandEntity camera;
    private static Perspective previousCameraType;
    private static float playerYaw;
    private static float playerPitch;

    private FreecamController() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> FreecamController.isActive());
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> level.isClient() && FreecamController.isActive() ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> level.isClient() && FreecamController.isActive() ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> level.isClient() && FreecamController.isActive() ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean isActive() {
        return camera != null;
    }

    public static void toggle(MinecraftClient client) {
        if (FreecamController.isActive()) {
            FreecamController.disable(client);
        } else {
            FreecamController.enable(client);
        }
    }

    public static void enable(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera != null || player == null || client.world == null) {
            return;
        }
        playerYaw = player.getYaw();
        playerPitch = player.getPitch();
        previousCameraType = client.options.getPerspective();
        camera = new ArmorStandEntity((World)client.world, player.getX(), player.getY(), player.getZ());
        camera.setYaw(playerYaw);
        camera.setPitch(playerPitch);
        FreecamController.camera.lastYaw = playerYaw;
        FreecamController.camera.lastPitch = playerPitch;
        camera.setInvisible(true);
        camera.setNoGravity(true);
        FreecamController.camera.noClip = true;
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity((Entity)camera);
        ArcaneClient.LOGGER.info("Freecam enabled");
    }

    public static void disable(MinecraftClient client) {
        if (camera == null) {
            return;
        }
        if (client.player != null) {
            client.player.setYaw(playerYaw);
            client.player.setPitch(playerPitch);
            client.player.lastYaw = playerYaw;
            client.player.lastPitch = playerPitch;
            client.setCameraEntity((Entity)client.player);
        } else {
            client.setCameraEntity(null);
        }
        client.options.setPerspective(previousCameraType);
        camera = null;
        ArcaneClient.LOGGER.info("Freecam disabled");
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera == null) {
            return;
        }
        if (player == null || client.world == null || camera.getEntityWorld() != client.world) {
            FreecamController.disable(client);
            return;
        }
        camera.setYaw(player.getYaw());
        camera.setPitch(player.getPitch());
        FreecamController.camera.lastYaw = camera.getYaw();
        FreecamController.camera.lastPitch = camera.getPitch();
        double forward = FreecamController.axis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed());
        double sideways = FreecamController.axis(client.options.rightKey.isPressed(), client.options.leftKey.isPressed());
        double vertical = FreecamController.axis(client.options.jumpKey.isPressed(), client.options.sneakKey.isPressed());
        if (forward == 0.0 && sideways == 0.0 && vertical == 0.0) {
            return;
        }
        Vec3d look = Vec3d.fromPolar((float)camera.getPitch(), (float)camera.getYaw());
        Vec3d right = Vec3d.fromPolar((float)0.0f, (float)(camera.getYaw() + 90.0f));
        Vec3d movement = look.multiply(forward).add(right.multiply(sideways)).add(0.0, vertical, 0.0);
        if (movement.lengthSquared() > 1.0) {
            movement = movement.normalize();
        }
        double speed = client.options.sprintKey.isPressed() ? 2.4 : 0.8;
        camera.setPosition(camera.getX() + movement.x * speed, camera.getY() + movement.y * speed, camera.getZ() + movement.z * speed);
    }

    private static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0 : (positive ? 1.0 : -1.0);
    }

    static {
        previousCameraType = Perspective.FIRST_PERSON;
    }
}
