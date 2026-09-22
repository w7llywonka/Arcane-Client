package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import java.util.Locale;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Reversible quality-of-life automation; it never keeps synthetic keys pressed outside live gameplay. */
@Environment(EnvType.CLIENT)
public final class QualityOfLifeController {
    private static boolean forcedForward;
    private static boolean forcedJump;
    private static boolean forcedSneak;
    private static boolean durabilityLatched;
    private static boolean deathLatched;
    private static boolean respawnRequested;
    private static int idleTicks;
    private static CameraType perspectiveBeforeFlight;
    private static LastDeath lastDeath;
    private static String sessionIdentity;

    private QualityOfLifeController() {
    }

    public static void reset(Minecraft client) {
        releaseForcedInputs(client);
        restorePerspective(client);
        durabilityLatched = false;
        deathLatched = false;
        respawnRequested = false;
        idleTicks = 0;
    }

    public static void onWorldChange(Minecraft client, ClientLevel world) {
        reset(client);
        String nextIdentity = world == null ? null : currentSessionIdentity(client);
        if (world == null || !Objects.equals(sessionIdentity, nextIdentity)) lastDeath = null;
        sessionIdentity = nextIdentity;
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        LocalPlayer player = client.player;
        boolean worldReady = player != null && client.level != null;
        boolean screenOpen = client.gui.screen() != null;

        handleToggleKeys(client, config, player);
        if (!worldReady) {
            reset(client);
            return;
        }

        boolean forceForwardNow = QualityOfLifePolicy.forceHeldInput(config.autoWalk, true, screenOpen);
        boolean forceSneakNow = QualityOfLifePolicy.forceHeldInput(config.autoSneak, true, screenOpen);
        boolean moving = client.options.keyUp.isDown() || client.options.keyDown.isDown()
            || client.options.keyLeft.isDown() || client.options.keyRight.isDown() || forceForwardNow;
        boolean forceJumpNow = QualityOfLifePolicy.forceJump(config.autoJump, true, screenOpen, moving, player.onGround());
        setForced(client.options.keyUp, forceForwardNow, forcedForward);
        setForced(client.options.keyJump, forceJumpNow, forcedJump);
        setForced(client.options.keyShift, forceSneakNow, forcedSneak);
        forcedForward = forceForwardNow;
        forcedJump = forceJumpNow;
        forcedSneak = forceSneakNow;

        tickAntiAfk(client, player, config, screenOpen);
        tickDurabilityGuard(client, player, config);
        tickDeath(client, player, config);
        tickAutoRespawn(client, player, config);
        tickElytraPerspective(client, player, config);
    }

    private static void handleToggleKeys(Minecraft client, ArcaneConfig config, LocalPlayer player) {
        while (ArcaneClient.keybinds().autoWalk().consumeClick()) {
            config.autoWalk = !config.autoWalk;
            config.save();
            message(player, "Auto Walk " + (config.autoWalk ? "on" : "off"), true);
        }
        while (ArcaneClient.keybinds().autoSneak().consumeClick()) {
            config.autoSneak = !config.autoSneak;
            config.save();
            message(player, "Auto Sneak " + (config.autoSneak ? "on" : "off"), true);
        }
        while (ArcaneClient.keybinds().coordinateClipboard().consumeClick()) {
            if (!config.coordinateClipboard || player == null) continue;
            if (!StreamerPrivacy.mayRevealSensitive(config.streamerMode)) {
                message(player, "Coordinate Clipboard is hidden by Streamer Mode", true);
                continue;
            }
            String value = QualityOfLifePolicy.coordinates(player.getX(), player.getY(), player.getZ());
            client.keyboardHandler.setClipboard(value);
            message(player, "Copied coordinates: " + value, true);
        }
    }

    private static void tickAntiAfk(Minecraft client, LocalPlayer player, ArcaneConfig config, boolean screenOpen) {
        if (!config.antiAfk || screenOpen) {
            idleTicks = 0;
            return;
        }
        boolean active = client.options.keyUp.isDown() || client.options.keyDown.isDown()
            || client.options.keyLeft.isDown() || client.options.keyRight.isDown()
            || client.options.keyJump.isDown() || client.options.keyAttack.isDown() || client.options.keyUse.isDown();
        if (active && !forcedForward && !forcedJump) {
            idleTicks = 0;
            return;
        }
        if (++idleTicks >= config.antiAfkSeconds * 20) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
            idleTicks = 0;
        }
    }

    private static void tickDurabilityGuard(Minecraft client, LocalPlayer player, ArcaneConfig config) {
        ItemStack held = player.getMainHandItem();
        boolean critical = shouldProtect(held);
        if (critical) {
            client.options.keyAttack.setDown(false);
            if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) client.options.keyUse.setDown(false);
            if (!durabilityLatched) message(player, "Durability Guard stopped " + held.getHoverName().getString(), true);
        }
        durabilityLatched = critical;
    }

    /** Shared by input release and the interaction-manager packet boundary. */
    public static boolean shouldProtect(ItemStack stack) {
        ArcaneConfig config = ArcaneClient.config();
        return config != null && stack != null && stack.isDamageableItem() && QualityOfLifePolicy.guardDurability(
            config.durabilityGuard,
            stack.getDamageValue(),
            stack.getMaxDamage(),
            config.durabilityGuardRemaining
        );
    }

    private static void tickDeath(Minecraft client, LocalPlayer player, ArcaneConfig config) {
        boolean dead = player.isDeadOrDying();
        if (!config.deathCoordinates) {
            lastDeath = null;
            deathLatched = dead;
            return;
        }
        if (dead && !deathLatched) {
            String dimension = client.level.dimension().identifier().getPath();
            lastDeath = new LastDeath(player.getX(), player.getY(), player.getZ(), dimension, System.currentTimeMillis());
            if (StreamerPrivacy.mayRevealSensitive(config.streamerMode)) {
                message(player, "Death coordinates: " + QualityOfLifePolicy.coordinates(player.getX(), player.getY(), player.getZ())
                    + " · " + dimension.toUpperCase(Locale.ROOT), false);
            }
        }
        deathLatched = dead;
    }

    private static void tickAutoRespawn(Minecraft client, LocalPlayer player, ArcaneConfig config) {
        if (!player.isDeadOrDying()) {
            respawnRequested = false;
            return;
        }
        if (config.autoRespawn && !respawnRequested) {
            player.respawn();
            client.gui.setScreen(null);
            respawnRequested = true;
        }
    }

    private static void tickElytraPerspective(Minecraft client, LocalPlayer player, ArcaneConfig config) {
        if (config.elytraPerspective && player.isFallFlying()) {
            if (perspectiveBeforeFlight == null && client.options.getCameraType().isFirstPerson()) {
                perspectiveBeforeFlight = client.options.getCameraType();
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else if (perspectiveBeforeFlight != null && client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                // Respect a manual perspective change made while gliding.
                perspectiveBeforeFlight = null;
            }
        } else {
            restorePerspective(client);
        }
    }

    private static void restorePerspective(Minecraft client) {
        if (perspectiveBeforeFlight != null) {
            client.options.setCameraType(perspectiveBeforeFlight);
            perspectiveBeforeFlight = null;
        }
    }

    private static void releaseForcedInputs(Minecraft client) {
        if (forcedForward) client.options.keyUp.setDown(false);
        if (forcedJump) client.options.keyJump.setDown(false);
        if (forcedSneak) client.options.keyShift.setDown(false);
        forcedForward = false;
        forcedJump = false;
        forcedSneak = false;
    }

    private static void setForced(net.minecraft.client.KeyMapping key, boolean now, boolean before) {
        if (now) key.setDown(true);
        else if (before) key.setDown(false);
    }

    private static void message(LocalPlayer player, String text, boolean actionbar) {
        if (player != null) {
            if (actionbar) player.sendOverlayMessage(Component.literal(text));
            else player.sendSystemMessage(Component.literal(text));
        }
    }

    private static String currentSessionIdentity(Minecraft client) {
        if (client.getCurrentServer() != null) return "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        return "local";
    }

    public static LastDeath lastDeath() {
        return lastDeath;
    }

    public record LastDeath(double x, double y, double z, String dimension, long recordedAtMillis) {
    }
}
