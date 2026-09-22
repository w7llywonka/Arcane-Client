package dev.arcaneclient.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.mixin.KeyBindingAccessor;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.lwjgl.sdl.SDLMouse;

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

    public static void reset(Minecraft client) {
        releaseOwned(client.options.keyUp, ownsForward);
        releaseOwned(client.options.keyDown, ownsBack);
        releaseOwned(client.options.keyLeft, ownsLeft);
        releaseOwned(client.options.keyRight, ownsRight);
        releaseOwned(client.options.keyJump, ownsJump);
        releaseOwned(client.options.keySprint, ownsSprint);
        releaseOwned(client.options.keyShift, ownsSneak);
        ownsForward = false;
        ownsBack = false;
        ownsLeft = false;
        ownsRight = false;
        ownsJump = false;
        ownsSprint = false;
        ownsSneak = false;
        parkourCooldown = 0;
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        LocalPlayer player = client.player;
        if (config == null || player == null || client.level == null || ArcaneSettingsScreen.isOpen(client)) {
            reset(client);
            return;
        }
        if (parkourCooldown > 0) parkourCooldown--;

        Screen screen = client.gui.screen();
        boolean inventoryMove = MovementAssistPolicy.inventoryMovement(
            config.inventoryMove,
            screen instanceof AbstractContainerScreen<?>,
            screen instanceof AnvilScreen,
            screen != null && screen.isPauseScreen()
        );
        boolean physicalForward = physicallyPressed(client, client.options.keyUp);
        boolean physicalBack = physicallyPressed(client, client.options.keyDown);
        boolean physicalLeft = physicallyPressed(client, client.options.keyLeft);
        boolean physicalRight = physicallyPressed(client, client.options.keyRight);
        boolean physicalJump = physicallyPressed(client, client.options.keyJump);
        boolean physicalSprint = physicallyPressed(client, client.options.keySprint);
        boolean physicalSneak = physicallyPressed(client, client.options.keyShift);

        boolean autoWalkHeld = config.autoWalk && screen == null;
        boolean autoSneakHeld = config.autoSneak && screen == null;
        ownsForward = applyOwned(client.options.keyUp, inventoryMove && physicalForward, ownsForward, physicalForward || autoWalkHeld);
        ownsBack = applyOwned(client.options.keyDown, inventoryMove && physicalBack, ownsBack, physicalBack);
        ownsLeft = applyOwned(client.options.keyLeft, inventoryMove && physicalLeft, ownsLeft, physicalLeft);
        ownsRight = applyOwned(client.options.keyRight, inventoryMove && physicalRight, ownsRight, physicalRight);
        ownsSprint = applyOwned(client.options.keySprint, inventoryMove && physicalSprint, ownsSprint, physicalSprint);

        boolean forward = inventoryMove ? physicalForward : client.options.keyUp.isDown();
        boolean back = inventoryMove ? physicalBack : client.options.keyDown.isDown();
        boolean left = inventoryMove ? physicalLeft : client.options.keyLeft.isDown();
        boolean right = inventoryMove ? physicalRight : client.options.keyRight.isDown();
        boolean moving = forward || back || left || right;
        Direction direction = movementDirection(player.getYRot(), forward, back, left, right);
        boolean supportAhead = direction.magnitude() < 0.01 || hasSupport(client, player, direction, 0.62);
        double parkourEdgeDistance = 0.48 + config.parkourAssistWindow * 0.04;
        boolean parkourSupportAhead = direction.magnitude() < 0.01 || hasSupport(client, player, direction, parkourEdgeDistance);
        boolean landingAhead = direction.magnitude() >= 0.01 && hasSupport(client, player, direction, 1.55);

        boolean safeWalk = MovementAssistPolicy.safeWalk(
            config.safeWalk,
            player.onGround(),
            player.isFallFlying(),
            player.isInWater(),
            moving,
            supportAhead
        );
        ownsSneak = applyOwned(client.options.keyShift, safeWalk, ownsSneak, physicalSneak || autoSneakHeld);

        boolean parkour = MovementAssistPolicy.parkourJump(
            config.parkourAssist,
            player.onGround(),
            moving,
            parkourSupportAhead,
            landingAhead,
            parkourCooldown
        );
        if (parkour) parkourCooldown = 8;
        boolean swim = MovementAssistPolicy.swimAscend(
            config.swimAssist,
            player.isInWater(),
            moving,
            screen != null
        );
        boolean jumpOwned = parkour || swim || inventoryMove && physicalJump;
        boolean autoJumpHeld = config.autoJump && screen == null && moving && player.onGround();
        ownsJump = applyOwned(client.options.keyJump, jumpOwned, ownsJump, physicalJump || autoJumpHeld);

        Entity vehicle = player.getVehicle();
        boolean controlsVehicle = vehicle != null && vehicle.getControllingPassenger() == player;
        boolean cruise = MovementAssistPolicy.vehicleCruise(config.vehicleCruise, controlsVehicle, screen != null);
        if (cruise && !ownsForward) ownsForward = applyOwned(client.options.keyUp, true, false, physicalForward);

    }

    private static boolean physicallyPressed(Minecraft client, KeyMapping binding) {
        InputConstants.Key key = ((KeyBindingAccessor)(Object)binding).arcaneclient$boundKey();
        if (key.getType() == InputConstants.Type.KEYBOARD) {
            return InputConstants.isKeyDown(key.getValue());
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            int button = key.getValue();
            return button > 0 && (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0;
        }
        return false;
    }

    private static boolean applyOwned(KeyMapping key, boolean desired, boolean ownedBefore, boolean preservePressed) {
        if (desired) {
            key.setDown(true);
            return true;
        }
        if (ownedBefore && !preservePressed) key.setDown(false);
        return false;
    }

    private static void releaseOwned(KeyMapping key, boolean owned) {
        if (owned) key.setDown(false);
    }

    private static Direction movementDirection(float yawDegrees, boolean forward, boolean back, boolean left, boolean right) {
        double localForward = (forward ? 1.0 : 0.0) - (back ? 1.0 : 0.0);
        double localStrafe = (left ? 1.0 : 0.0) - (right ? 1.0 : 0.0);
        double length = Math.hypot(localForward, localStrafe);
        if (length < 0.01) return new Direction(0.0, 0.0);
        localForward /= length;
        localStrafe /= length;
        double yaw = Math.toRadians(Mth.wrapDegrees(yawDegrees));
        return new Direction(
            -Math.sin(yaw) * localForward - Math.cos(yaw) * localStrafe,
            Math.cos(yaw) * localForward - Math.sin(yaw) * localStrafe
        );
    }

    private static boolean hasSupport(Minecraft client, LocalPlayer player, Direction direction, double distance) {
        double offsetX = direction.x() * distance;
        double offsetZ = direction.z() * distance;
        double inset = 0.08;
        double[] sampleX = {player.getBoundingBox().minX + inset, player.getBoundingBox().maxX - inset};
        double[] sampleZ = {player.getBoundingBox().minZ + inset, player.getBoundingBox().maxZ - inset};
        double footY = player.getBoundingBox().minY - 0.08;
        for (double x : sampleX) {
            for (double z : sampleZ) {
                BlockPos pos = BlockPos.containing(x + offsetX, footY, z + offsetZ);
                if (!client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty()) return true;
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
