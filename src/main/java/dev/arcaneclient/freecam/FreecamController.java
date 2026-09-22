package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Smooth detached camera with server-valid directional mining from the player's body. */
@Environment(EnvType.CLIENT)
public final class FreecamController {
    private static boolean active;
    private static boolean previousChunkCullingEnabled = true;
    private static CameraType previousPerspective = CameraType.FIRST_PERSON;
    private static float playerYaw;
    private static float playerPitch;
    private static float cameraYaw;
    private static float cameraPitch;
    private static Vec3 previousPosition = Vec3.ZERO;
    private static Vec3 position = Vec3.ZERO;
    private static Vec3 velocity = Vec3.ZERO;

    private FreecamController() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> DetachedCameraInteraction.isActive());
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> world.isClientSide() && FreecamController.isActive() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> world.isClientSide() && FreecamController.isActive() ? InteractionResult.FAIL : InteractionResult.PASS);
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

    public static void toggle(Minecraft client) {
        if (isActive()) disable(client); else enable(client);
    }

    public static void enable(Minecraft client) {
        LocalPlayer player = client.player;
        if (active || player == null || client.level == null) return;
        FreelookController.disable(client);
        AutoToolController.restore(client);
        playerYaw = player.getYRot();
        playerPitch = player.getXRot();
        cameraYaw = playerYaw;
        cameraPitch = playerPitch;
        previousPerspective = client.options.getCameraType();
        previousChunkCullingEnabled = client.smartCull;
        position = player.getEyePosition();
        previousPosition = position;
        active = true;
        FreecamVisualBody.spawn(client, player);
        velocity = Vec3.ZERO;
        DetachedCameraInteraction.stopMining(client);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.setCameraEntity(player);
        // Vanilla's section-occlusion graph assumes an ordinary player camera. Underground,
        // that graph can hide loaded sections behind solid chunks and leave large black gaps
        // when our rendered camera moves through walls. Freecam needs the complete frustum.
        client.smartCull = false;
        client.levelRenderer.invalidateCompiledGeometry(client.level, client.options, client.gameRenderer.mainCamera(), client.getBlockColors());
        ArcaneClient.LOGGER.info("Freecam enabled");
    }

    public static void disable(Minecraft client) {
        DetachedCameraInteraction.stopMining(client);
        if (!active) {
            FreecamVisualBody.remove();
            return;
        }
        active = false;
        FreecamVisualBody.remove();
        client.smartCull = previousChunkCullingEnabled;
        if (client.level != null) {
            client.levelRenderer.invalidateCompiledGeometry(client.level, client.options, client.gameRenderer.mainCamera(), client.getBlockColors());
        }
        if (client.player != null) {
            client.player.setYRot(playerYaw);
            client.player.setXRot(playerPitch);
            client.player.yRotO = playerYaw;
            client.player.xRotO = playerPitch;
            client.setCameraEntity(client.player);
        } else {
            client.setCameraEntity(null);
        }
        client.options.setCameraType(previousPerspective);
        previousPosition = Vec3.ZERO;
        position = Vec3.ZERO;
        velocity = Vec3.ZERO;
        ArcaneClient.LOGGER.info("Freecam disabled");
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (!active) return;
        if (player == null || client.level == null) {
            disable(client);
            return;
        }

        if (client.getCameraEntity() != player) client.setCameraEntity(player);
        // Keep this authoritative if another mod or a vanilla debug toggle changes it while
        // Freecam is active. WorldRenderer already recenters its section graph from CameraMixin's
        // detached position every eight blocks.
        client.smartCull = false;
        // The real player still receives gravity and server corrections while movement input is
        // detached. Keep the rendered copy on that authoritative position so it cannot hover at
        // the activation coordinate after the player lands or is moved by the server.
        FreecamVisualBody.sync(player);

        player.setSprinting(false);
        double forward = axis(client.options.keyUp.isDown(), client.options.keyDown.isDown());
        double sideways = axis(client.options.keyRight.isDown(), client.options.keyLeft.isDown());
        double vertical = axis(client.options.keyJump.isDown(), client.options.keyShift.isDown());
        boolean moving = forward != 0.0 || sideways != 0.0 || vertical != 0.0;
        Vec3 target = Vec3.ZERO;
        if (moving) {
            Vec3 direction = FreecamNavigation.direction(cameraYaw, forward, sideways, vertical);
            double speed = FreecamSpeed.blocksPerTick(ArcaneClient.config().freecamSpeed);
            if (client.options.keySprint.isDown()) speed *= 3.0;
            target = direction.scale(speed);
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

    public static Vec3 cameraPosition(float tickProgress) {
        double progress = Math.clamp(tickProgress, 0.0f, 1.0f);
        return new Vec3(
            Mth.lerp(progress, previousPosition.x, position.x),
            Mth.lerp(progress, previousPosition.y, position.y),
            Mth.lerp(progress, previousPosition.z, position.z)
        );
    }

    /** Consumes the wheel only while freecam is active and persists the selected speed. */
    public static boolean onScroll(double vertical) {
        if (!isActive() || vertical == 0.0) return false;
        int previous = ArcaneClient.config().freecamSpeed;
        ArcaneClient.config().freecamSpeed = FreecamSpeed.adjust(previous, vertical);
        if (ArcaneClient.config().freecamSpeed != previous) {
            ArcaneClient.config().save();
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Freecam speed: " + ArcaneClient.config().freecamSpeed));
            }
        }
        return true;
    }

    private static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0 : positive ? 1.0 : -1.0;
    }
}
