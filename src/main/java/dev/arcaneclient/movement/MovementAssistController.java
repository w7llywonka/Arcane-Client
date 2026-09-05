package dev.arcaneclient.movement;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.mixin.KeyBindingAccessor;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Coordinates movement assists through one owner so modules never leave synthetic keys held.
 * Every decision is recomputed each tick and relinquished on world/screen changes.
 */
@Environment(EnvType.CLIENT)
public final class MovementAssistController {
    private static boolean ownsForward;
    private static boolean ownsBack;
    private static boolean ownsLeft;
    private static boolean ownsRight;
    private static boolean ownsJump;
    private static boolean ownsSprint;
    private static boolean ownsSneak;
    private static int parkourCooldown;

    private MovementAssistController() {
    }

    public static void reset(MinecraftClient client) {
        releaseOwned(client.options.forwardKey, ownsForward);
        releaseOwned(client.options.backKey, ownsBack);
        releaseOwned(client.options.leftKey, ownsLeft);
        releaseOwned(client.options.rightKey, ownsRight);
        releaseOwned(client.options.jumpKey, ownsJump);
        releaseOwned(client.options.sprintKey, ownsSprint);
        releaseOwned(client.options.sneakKey, ownsSneak);
        ownsForward = false;
        ownsBack = false;
        ownsLeft = false;
        ownsRight = false;
        ownsJump = false;
        ownsSprint = false;
        ownsSneak = false;
        parkourCooldown = 0;
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        ClientPlayerEntity player = client.player;
        if (config == null || player == null || client.world == null || ArcaneSettingsScreen.isOpen(client)) {
            reset(client);
            return;
        }
        if (parkourCooldown > 0) parkourCooldown--;

        Screen screen = client.currentScreen;
        boolean inventoryMove = MovementAssistPolicy.inventoryMovement(
            config.inventoryMove,
            screen instanceof HandledScreen<?>,
            screen instanceof AnvilScreen,
            screen != null && screen.shouldPause()
        );
        boolean physicalForward = physicallyPressed(client, client.options.forwardKey);
        boolean physicalBack = physicallyPressed(client, client.options.backKey);
        boolean physicalLeft = physicallyPressed(client, client.options.leftKey);
        boolean physicalRight = physicallyPressed(client, client.options.rightKey);
        boolean physicalJump = physicallyPressed(client, client.options.jumpKey);
        boolean physicalSprint = physicallyPressed(client, client.options.sprintKey);
        boolean physicalSneak = physicallyPressed(client, client.options.sneakKey);

        boolean autoWalkHeld = config.autoWalk && screen == null;
        boolean autoSneakHeld = config.autoSneak && screen == null;
        ownsForward = applyOwned(client.options.forwardKey, inventoryMove && physicalForward, ownsForward, physicalForward || autoWalkHeld);
        ownsBack = applyOwned(client.options.backKey, inventoryMove && physicalBack, ownsBack, physicalBack);
        ownsLeft = applyOwned(client.options.leftKey, inventoryMove && physicalLeft, ownsLeft, physicalLeft);
        ownsRight = applyOwned(client.options.rightKey, inventoryMove && physicalRight, ownsRight, physicalRight);
        ownsSprint = applyOwned(client.options.sprintKey, inventoryMove && physicalSprint, ownsSprint, physicalSprint);

        boolean forward = inventoryMove ? physicalForward : client.options.forwardKey.isPressed();
        boolean back = inventoryMove ? physicalBack : client.options.backKey.isPressed();
        boolean left = inventoryMove ? physicalLeft : client.options.leftKey.isPressed();
        boolean right = inventoryMove ? physicalRight : client.options.rightKey.isPressed();
        boolean moving = forward || back || left || right;
        Direction direction = movementDirection(player.getYaw(), forward, back, left, right);
        boolean supportAhead = direction.magnitude() < 0.01 || hasSupport(client, player, direction, 0.62);
        double parkourEdgeDistance = 0.48 + config.parkourAssistWindow * 0.04;
        boolean parkourSupportAhead = direction.magnitude() < 0.01 || hasSupport(client, player, direction, parkourEdgeDistance);
        boolean landingAhead = direction.magnitude() >= 0.01 && hasSupport(client, player, direction, 1.55);

        boolean safeWalk = MovementAssistPolicy.safeWalk(
            config.safeWalk,
            player.isOnGround(),
            player.isGliding(),
            player.isTouchingWater(),
            moving,
            supportAhead
        );
        ownsSneak = applyOwned(client.options.sneakKey, safeWalk, ownsSneak, physicalSneak || autoSneakHeld);

        boolean parkour = MovementAssistPolicy.parkourJump(
            config.parkourAssist,
            player.isOnGround(),
            moving,
            parkourSupportAhead,
            landingAhead,
            parkourCooldown
        );
        if (parkour) parkourCooldown = 8;
        boolean swim = MovementAssistPolicy.swimAscend(
            config.swimAssist,
            player.isTouchingWater(),
            moving,
            screen != null
        );
        boolean jumpOwned = parkour || swim || inventoryMove && physicalJump;
        boolean autoJumpHeld = config.autoJump && screen == null && moving && player.isOnGround();
        ownsJump = applyOwned(client.options.jumpKey, jumpOwned, ownsJump, physicalJump || autoJumpHeld);

        Entity vehicle = player.getVehicle();
        boolean controlsVehicle = vehicle != null && vehicle.getControllingPassenger() == player;
        boolean cruise = MovementAssistPolicy.vehicleCruise(config.vehicleCruise, controlsVehicle, screen != null);
        if (cruise && !ownsForward) ownsForward = applyOwned(client.options.forwardKey, true, false, physicalForward);

    }

    private static boolean physicallyPressed(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = ((KeyBindingAccessor)(Object)binding).arcaneclient$boundKey();
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            return InputUtil.isKeyPressed(client.getWindow(), key.getCode());
        }
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private static boolean applyOwned(KeyBinding key, boolean desired, boolean ownedBefore, boolean preservePressed) {
        if (desired) {
            key.setPressed(true);
            return true;
        }
        if (ownedBefore && !preservePressed) key.setPressed(false);
        return false;
    }

    private static void releaseOwned(KeyBinding key, boolean owned) {
        if (owned) key.setPressed(false);
    }

    private static Direction movementDirection(float yawDegrees, boolean forward, boolean back, boolean left, boolean right) {
        double localForward = (forward ? 1.0 : 0.0) - (back ? 1.0 : 0.0);
        double localStrafe = (left ? 1.0 : 0.0) - (right ? 1.0 : 0.0);
        double length = Math.hypot(localForward, localStrafe);
        if (length < 0.01) return new Direction(0.0, 0.0);
        localForward /= length;
        localStrafe /= length;
        double yaw = Math.toRadians(MathHelper.wrapDegrees(yawDegrees));
        return new Direction(
            -Math.sin(yaw) * localForward - Math.cos(yaw) * localStrafe,
            Math.cos(yaw) * localForward - Math.sin(yaw) * localStrafe
        );
    }

    private static boolean hasSupport(MinecraftClient client, ClientPlayerEntity player, Direction direction, double distance) {
        double offsetX = direction.x() * distance;
        double offsetZ = direction.z() * distance;
        double inset = 0.08;
        double[] sampleX = {player.getBoundingBox().minX + inset, player.getBoundingBox().maxX - inset};
        double[] sampleZ = {player.getBoundingBox().minZ + inset, player.getBoundingBox().maxZ - inset};
        double footY = player.getBoundingBox().minY - 0.08;
        for (double x : sampleX) {
            for (double z : sampleZ) {
                BlockPos pos = BlockPos.ofFloored(x + offsetX, footY, z + offsetZ);
                if (!client.world.getBlockState(pos).getCollisionShape(client.world, pos).isEmpty()) return true;
            }
        }
        return false;
    }

    private record Direction(double x, double z) {
        double magnitude() {
            return Math.hypot(x, z);
        }
    }
}
