package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Smooth detached camera with server-valid directional mining from the player's body. */
@Environment(EnvType.CLIENT)
public final class FreecamController {
    private static boolean active;
    private static boolean previousChunkCullingEnabled = true;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;
    private static float playerYaw;
    private static float playerPitch;
    private static float cameraYaw;
    private static float cameraPitch;
    private static Vec3d previousPosition = Vec3d.ZERO;
    private static Vec3d position = Vec3d.ZERO;
    private static Vec3d velocity = Vec3d.ZERO;

    private FreecamController() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> DetachedCameraInteraction.isActive());
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> world.isClient() && FreecamController.isActive() ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> world.isClient() && FreecamController.isActive() ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean isActive() {
        return active;
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
        if (active || player == null || client.world == null) return;
        FreelookController.disable(client);
        AutoToolController.restore(client);
        playerYaw = player.getYaw();
        playerPitch = player.getPitch();
        cameraYaw = playerYaw;
        cameraPitch = playerPitch;
        previousPerspective = client.options.getPerspective();
        previousChunkCullingEnabled = client.chunkCullingEnabled;
        position = player.getEyePos();
        previousPosition = position;
        active = true;
        FreecamVisualBody.spawn(client, player);
        velocity = Vec3d.ZERO;
        DetachedCameraInteraction.stopMining(client);
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(player);
        // Vanilla's section-occlusion graph assumes an ordinary player camera. Underground,
        // that graph can hide loaded sections behind solid chunks and leave large black gaps
        // when our rendered camera moves through walls. Freecam needs the complete frustum.
        client.chunkCullingEnabled = false;
        client.worldRenderer.scheduleTerrainUpdate();
        ArcaneClient.LOGGER.info("Freecam enabled");
    }

    public static void disable(MinecraftClient client) {
        DetachedCameraInteraction.stopMining(client);
        if (!active) {
            FreecamVisualBody.remove();
            return;
        }
        active = false;
        FreecamVisualBody.remove();
        client.chunkCullingEnabled = previousChunkCullingEnabled;
        client.worldRenderer.scheduleTerrainUpdate();
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
        previousPosition = Vec3d.ZERO;
        position = Vec3d.ZERO;
        velocity = Vec3d.ZERO;
        ArcaneClient.LOGGER.info("Freecam disabled");
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!active) return;
        if (player == null || client.world == null) {
            disable(client);
            return;
        }

        if (client.getCameraEntity() != player) client.setCameraEntity(player);
        // Keep this authoritative if another mod or a vanilla debug toggle changes it while
        // Freecam is active. WorldRenderer already recenters its section graph from CameraMixin's
        // detached position every eight blocks.
        client.chunkCullingEnabled = false;
        // The real player still receives gravity and server corrections while movement input is
        // detached. Keep the rendered copy on that authoritative position so it cannot hover at
        // the activation coordinate after the player lands or is moved by the server.
        FreecamVisualBody.sync(player);

        player.setSprinting(false);
        double forward = axis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed());
        double sideways = axis(client.options.rightKey.isPressed(), client.options.leftKey.isPressed());
        double vertical = axis(client.options.jumpKey.isPressed(), client.options.sneakKey.isPressed());
        boolean moving = forward != 0.0 || sideways != 0.0 || vertical != 0.0;
        Vec3d target = Vec3d.ZERO;
        if (moving) {
            Vec3d direction = FreecamNavigation.direction(cameraYaw, forward, sideways, vertical);
            double speed = ArcaneClient.config().freecamSpeed / 10.0;
            if (client.options.sprintKey.isPressed()) speed *= 3.0;
            target = direction.multiply(speed);
        }
        velocity = FreecamMotion.step(velocity, target, moving);
        previousPosition = position;
        position = position.add(velocity);
        DetachedCameraInteraction.tickMining(
            client,
            player,
            playerYaw,
            playerPitch,
            ArcaneClient.config().freecamMining
        );
    }

    /** Receives vanilla's sensitivity-adjusted mouse deltas and rotates only the detached camera. */
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

    static float playerYaw() {
        return playerYaw;
    }

    static float playerPitch() {
        return playerPitch;
    }

    public static Vec3d cameraPosition(float tickProgress) {
        double progress = Math.clamp(tickProgress, 0.0f, 1.0f);
        return new Vec3d(
            MathHelper.lerp(progress, previousPosition.x, position.x),
            MathHelper.lerp(progress, previousPosition.y, position.y),
            MathHelper.lerp(progress, previousPosition.z, position.z)
        );
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
