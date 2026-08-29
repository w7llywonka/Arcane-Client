package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.utility.AutoToolController;
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

/** Smooth detached camera with server-valid directional mining from the player's body. */
@Environment(EnvType.CLIENT)
public final class FreecamController {
    private static ArmorStandEntity camera;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;
    private static float playerYaw;
    private static float playerPitch;
    private static Vec3d velocity = Vec3d.ZERO;

    private FreecamController() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> DetachedCameraInteraction.isActive());
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> world.isClient() && DetachedCameraInteraction.isActive() ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> world.isClient() && DetachedCameraInteraction.isActive() ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> world.isClient() && DetachedCameraInteraction.isActive() ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean isActive() {
        return camera != null;
    }

    public static boolean hasVisualBody() {
        return FreecamVisualBody.isPresent();
    }

    public static boolean isVisualBody(Entity entity) {
        return FreecamVisualBody.owns(entity);
    }

    public static Entity visualBodyEntity() {
        return FreecamVisualBody.entity();
    }

    public static void toggle(MinecraftClient client) {
        if (isActive()) disable(client); else enable(client);
    }

    public static void enable(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera != null || player == null || client.world == null) return;
        FreelookController.disable(client);
        AutoToolController.restore(client);
        playerYaw = player.getYaw();
        playerPitch = player.getPitch();
        previousPerspective = client.options.getPerspective();
        camera = new ArmorStandEntity((World) client.world, player.getX(), player.getY(), player.getZ());
        DetachedCameraPose.initialize(
            camera,
            player.getX(),
            player.getEyeY() - camera.getStandingEyeHeight(),
            player.getZ(),
            playerYaw,
            playerPitch
        );
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.noClip = true;
        FreecamVisualBody.spawn(client, player);
        velocity = Vec3d.ZERO;
        DetachedCameraInteraction.stopMining(client);
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity((Entity) camera);
        ArcaneClient.LOGGER.info("Freecam enabled");
    }

    public static void disable(MinecraftClient client) {
        DetachedCameraInteraction.stopMining(client);
        if (camera == null) {
            FreecamVisualBody.remove();
            return;
        }
        FreecamVisualBody.remove();
        if (client.player != null) {
            client.player.setYaw(playerYaw);
            client.player.setPitch(playerPitch);
            client.player.lastYaw = playerYaw;
            client.player.lastPitch = playerPitch;
            client.setCameraEntity(client.player);
        } else {
            client.setCameraEntity(null);
        }
        client.options.setPerspective(previousPerspective);
        camera = null;
        velocity = Vec3d.ZERO;
        ArcaneClient.LOGGER.info("Freecam disabled");
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera == null) return;
        if (player == null || client.world == null || camera.getEntityWorld() != client.world) {
            disable(client);
            return;
        }

        player.setSprinting(false);
        double forward = axis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed());
        double sideways = axis(client.options.rightKey.isPressed(), client.options.leftKey.isPressed());
        double vertical = axis(client.options.jumpKey.isPressed(), client.options.sneakKey.isPressed());
        boolean moving = forward != 0.0 || sideways != 0.0 || vertical != 0.0;
        Vec3d target = Vec3d.ZERO;
        if (moving) {
            Vec3d direction = FreecamNavigation.direction(camera.getYaw(), forward, sideways, vertical);
            double speed = ArcaneClient.config().freecamSpeed / 10.0;
            if (client.options.sprintKey.isPressed()) speed *= 3.0;
            target = direction.multiply(speed);
        }
        velocity = FreecamMotion.step(velocity, target, moving);
        DetachedCameraPose.advance(
            camera,
            camera.getX() + velocity.x,
            camera.getY() + velocity.y,
            camera.getZ() + velocity.z
        );
        DetachedCameraInteraction.tickMining(
            client,
            player,
            camera.getYaw(),
            camera.getPitch(),
            ArcaneClient.config().freecamMining
        );
    }

    /** Receives vanilla's sensitivity-adjusted mouse deltas and rotates only the detached camera. */
    public static boolean changeLookDirection(double cursorDeltaX, double cursorDeltaY) {
        if (camera == null) return false;
        CameraRotation.Angles next = CameraRotation.apply(
            camera.getYaw(), camera.getPitch(), cursorDeltaX, cursorDeltaY
        );
        DetachedCameraPose.rotate(camera, next.yaw(), next.pitch());
        return true;
    }

    /** Consumes the wheel only while freecam is active and persists the selected speed. */
    public static boolean onScroll(double vertical) {
        if (!isActive() || vertical == 0.0) return false;
        int previous = ArcaneClient.config().freecamSpeed;
        ArcaneClient.config().freecamSpeed = FreecamSpeed.adjust(previous, vertical);
        if (ArcaneClient.config().freecamSpeed != previous) {
            ArcaneClient.config().save();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("Freecam speed: " + ArcaneClient.config().freecamSpeed), true);
            }
        }
        return true;
    }

    private static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0 : positive ? 1.0 : -1.0;
    }
}
