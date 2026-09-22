package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Handles camera toggles on the raw press event instead of waiting for the next client tick. */
@Environment(EnvType.CLIENT)
public final class CameraToggleInput {
    private CameraToggleInput() {
    }

    public static void onKey(Minecraft client, int action, KeyEvent input) {
        if (!canToggle(client, action)) return;
        if (ArcaneClient.keybinds().freecam().matches(input)) {
            handleKey(client, ArcaneClient.keybinds().freecam(), input, true);
        } else if (ArcaneClient.keybinds().freelook().matches(input)) {
            handleKey(client, ArcaneClient.keybinds().freelook(), input, false);
        }
    }

    public static void onMouse(Minecraft client, int action, MouseButtonInfo input) {
        if (!canToggle(client, action)) return;
        MouseButtonEvent click = new MouseButtonEvent(0.0, 0.0, input);
        if (ArcaneClient.keybinds().freecam().matchesMouse(click)) {
            handleMouse(client, ArcaneClient.keybinds().freecam(), true);
        } else if (ArcaneClient.keybinds().freelook().matchesMouse(click)) {
            handleMouse(client, ArcaneClient.keybinds().freelook(), false);
        }
    }

    private static void handleKey(Minecraft client, KeyMapping binding, KeyEvent input, boolean freecam) {
        var config = ArcaneClient.config().visualAdditions;
        if (config.advancedChordBinds && !modifierDown(input, config.chordModifier)) {
            drain(binding);
            return;
        }
        if (config.sdlInputProfile != 0 && !config.advancedChordBinds) return;
        if (freecam) toggleFreecam(client, binding); else toggleFreelook(client, binding);
    }

    private static void handleMouse(Minecraft client, KeyMapping binding, boolean freecam) {
        var config = ArcaneClient.config().visualAdditions;
        if (config.advancedChordBinds && !modifierDown(config.chordModifier)) {
            drain(binding);
            return;
        }
        if (config.sdlInputProfile != 0 && !config.advancedChordBinds) return;
        if (freecam) toggleFreecam(client, binding); else toggleFreelook(client, binding);
    }

    private static boolean modifierDown(KeyEvent input, int modifier) {
        return switch (Math.clamp(modifier, 0, 2)) {
            case 1 -> input.hasAltDown();
            case 2 -> input.hasShiftDown();
            default -> input.hasControlDown();
        };
    }

    private static boolean modifierDown(int modifier) {
        return switch (Math.clamp(modifier, 0, 2)) {
            case 1 -> InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
            case 2 -> InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
            default -> InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        };
    }

    private static boolean canToggle(Minecraft client, int action) {
        return action == InputConstants.PRESS
            && client.gui.screen() == null
            && client.player != null
            && ArcaneClient.keybinds() != null;
    }

    private static void toggleFreecam(Minecraft client, KeyMapping binding) {
        drain(binding);
        FreecamController.toggle(client);
        client.player.sendOverlayMessage(Component.literal("Freecam " + (FreecamController.isActive() ? "on" : "off")));
    }

    private static void toggleFreelook(Minecraft client, KeyMapping binding) {
        drain(binding);
        FreelookController.toggle(client);
        client.player.sendOverlayMessage(Component.literal("Freelook " + (FreelookController.isActive() ? "on" : "off")));
    }

    private static void drain(KeyMapping binding) {
        while (binding.consumeClick()) {
            // Prevent END_CLIENT_TICK from toggling the mode a second time.
        }
    }
}
