package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import java.util.Locale;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

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
    private static Perspective perspectiveBeforeFlight;
    private static LastDeath lastDeath;
    private static String sessionIdentity;

    private QualityOfLifeController() {
    }

    public static void reset(MinecraftClient client) {
        releaseForcedInputs(client);
        restorePerspective(client);
        durabilityLatched = false;
        deathLatched = false;
        respawnRequested = false;
        idleTicks = 0;
    }

    public static void onWorldChange(MinecraftClient client, ClientWorld world) {
        reset(client);
        String nextIdentity = world == null ? null : currentSessionIdentity(client);
        if (world == null || !Objects.equals(sessionIdentity, nextIdentity)) lastDeath = null;
        sessionIdentity = nextIdentity;
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        ClientPlayerEntity player = client.player;
        boolean worldReady = player != null && client.world != null;
        boolean screenOpen = client.currentScreen != null;

        handleToggleKeys(client, config, player);
        if (!worldReady) {
            reset(client);
            return;
        }

        boolean forceForwardNow = QualityOfLifePolicy.forceHeldInput(config.autoWalk, true, screenOpen);
        boolean forceSneakNow = QualityOfLifePolicy.forceHeldInput(config.autoSneak, true, screenOpen);
        boolean moving = client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
            || client.options.leftKey.isPressed() || client.options.rightKey.isPressed() || forceForwardNow;
        boolean forceJumpNow = QualityOfLifePolicy.forceJump(config.autoJump, true, screenOpen, moving, player.isOnGround());
        setForced(client.options.forwardKey, forceForwardNow, forcedForward);
        setForced(client.options.jumpKey, forceJumpNow, forcedJump);
        setForced(client.options.sneakKey, forceSneakNow, forcedSneak);
        forcedForward = forceForwardNow;
        forcedJump = forceJumpNow;
        forcedSneak = forceSneakNow;

        tickAntiAfk(client, player, config, screenOpen);
        tickDurabilityGuard(client, player, config);
        tickDeath(client, player, config);
        tickAutoRespawn(client, player, config);
        tickElytraPerspective(client, player, config);
    }

    private static void handleToggleKeys(MinecraftClient client, ArcaneConfig config, ClientPlayerEntity player) {
        while (ArcaneClient.keybinds().autoWalk().wasPressed()) {
            config.autoWalk = !config.autoWalk;
            config.save();
            message(player, "Auto Walk " + (config.autoWalk ? "on" : "off"), true);
        }
        while (ArcaneClient.keybinds().autoSneak().wasPressed()) {
            config.autoSneak = !config.autoSneak;
            config.save();
            message(player, "Auto Sneak " + (config.autoSneak ? "on" : "off"), true);
        }
        while (ArcaneClient.keybinds().coordinateClipboard().wasPressed()) {
            if (!config.coordinateClipboard || player == null) continue;
            if (!StreamerPrivacy.mayRevealSensitive(config.streamerMode)) {
                message(player, "Coordinate Clipboard is hidden by Streamer Mode", true);
                continue;
            }
            String value = QualityOfLifePolicy.coordinates(player.getX(), player.getY(), player.getZ());
            client.keyboard.setClipboard(value);
            message(player, "Copied coordinates: " + value, true);
        }
    }

    private static void tickAntiAfk(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config, boolean screenOpen) {
        if (!config.antiAfk || screenOpen) {
            idleTicks = 0;
            return;
        }
        boolean active = client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
            || client.options.leftKey.isPressed() || client.options.rightKey.isPressed()
            || client.options.jumpKey.isPressed() || client.options.attackKey.isPressed() || client.options.useKey.isPressed();
        if (active && !forcedForward && !forcedJump) {
            idleTicks = 0;
            return;
        }
        if (++idleTicks >= config.antiAfkSeconds * 20) {
            player.swingHand(Hand.MAIN_HAND);
            idleTicks = 0;
        }
    }

    private static void tickDurabilityGuard(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        ItemStack held = player.getMainHandStack();
        boolean critical = shouldProtect(held);
        if (critical) {
            client.options.attackKey.setPressed(false);
            if (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND) client.options.useKey.setPressed(false);
            if (!durabilityLatched) message(player, "Durability Guard stopped " + held.getName().getString(), true);
        }
        durabilityLatched = critical;
    }

    /** Shared by input release and the interaction-manager packet boundary. */
    public static boolean shouldProtect(ItemStack stack) {
        ArcaneConfig config = ArcaneClient.config();
        return config != null && stack != null && stack.isDamageable() && QualityOfLifePolicy.guardDurability(
            config.durabilityGuard,
            stack.getDamage(),
            stack.getMaxDamage(),
            config.durabilityGuardRemaining
        );
    }

    private static void tickDeath(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        boolean dead = player.isDead();
        if (!config.deathCoordinates) {
            lastDeath = null;
            deathLatched = dead;
            return;
        }
        if (dead && !deathLatched) {
            String dimension = client.world.getRegistryKey().getValue().getPath();
            lastDeath = new LastDeath(player.getX(), player.getY(), player.getZ(), dimension, System.currentTimeMillis());
            if (StreamerPrivacy.mayRevealSensitive(config.streamerMode)) {
                message(player, "Death coordinates: " + QualityOfLifePolicy.coordinates(player.getX(), player.getY(), player.getZ())
                    + " · " + dimension.toUpperCase(Locale.ROOT), false);
            }
        }
        deathLatched = dead;
    }

    private static void tickAutoRespawn(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        if (!player.isDead()) {
            respawnRequested = false;
            return;
        }
        if (config.autoRespawn && !respawnRequested) {
            player.requestRespawn();
            client.setScreen(null);
            respawnRequested = true;
        }
    }

    private static void tickElytraPerspective(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        if (config.elytraPerspective && player.isGliding()) {
            if (perspectiveBeforeFlight == null && client.options.getPerspective().isFirstPerson()) {
                perspectiveBeforeFlight = client.options.getPerspective();
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            } else if (perspectiveBeforeFlight != null && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
                // Respect a manual perspective change made while gliding.
                perspectiveBeforeFlight = null;
            }
        } else {
            restorePerspective(client);
        }
    }

    private static void restorePerspective(MinecraftClient client) {
        if (perspectiveBeforeFlight != null) {
            client.options.setPerspective(perspectiveBeforeFlight);
            perspectiveBeforeFlight = null;
        }
    }

    private static void releaseForcedInputs(MinecraftClient client) {
        if (forcedForward) client.options.forwardKey.setPressed(false);
        if (forcedJump) client.options.jumpKey.setPressed(false);
        if (forcedSneak) client.options.sneakKey.setPressed(false);
        forcedForward = false;
        forcedJump = false;
        forcedSneak = false;
    }

    private static void setForced(net.minecraft.client.option.KeyBinding key, boolean now, boolean before) {
        if (now) key.setPressed(true);
        else if (before) key.setPressed(false);
    }

    private static void message(ClientPlayerEntity player, String text, boolean actionbar) {
        if (player != null) player.sendMessage(Text.literal(text), actionbar);
    }

    private static String currentSessionIdentity(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) return "server:" + client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        return "local";
    }

    public static LastDeath lastDeath() {
        return lastDeath;
    }

    public record LastDeath(double x, double y, double z, String dimension, long recordedAtMillis) {
    }
}
