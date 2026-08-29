package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Handles camera toggles on the raw press event instead of waiting for the next client tick. */
@Environment(EnvType.CLIENT)
public final class CameraToggleInput {
    private CameraToggleInput() {
    }

    public static void onKey(MinecraftClient client, int action, KeyInput input) {
        if (!canToggle(client, action)) return;
        if (ArcaneClient.keybinds().freecam().matchesKey(input)) {
            toggleFreecam(client, ArcaneClient.keybinds().freecam());
        } else if (ArcaneClient.keybinds().freelook().matchesKey(input)) {
            toggleFreelook(client, ArcaneClient.keybinds().freelook());
        }
    }

    public static void onMouse(MinecraftClient client, int action, MouseInput input) {
        if (!canToggle(client, action)) return;
        Click click = new Click(0.0, 0.0, input);
        if (ArcaneClient.keybinds().freecam().matchesMouse(click)) {
            toggleFreecam(client, ArcaneClient.keybinds().freecam());
        } else if (ArcaneClient.keybinds().freelook().matchesMouse(click)) {
            toggleFreelook(client, ArcaneClient.keybinds().freelook());
        }
    }

    private static boolean canToggle(MinecraftClient client, int action) {
        return action == GLFW.GLFW_PRESS
            && client.currentScreen == null
            && client.player != null
            && ArcaneClient.keybinds() != null;
    }

    private static void toggleFreecam(MinecraftClient client, KeyBinding binding) {
        drain(binding);
        FreecamController.toggle(client);
        client.player.sendMessage(Text.literal("Freecam " + (FreecamController.isActive() ? "on" : "off")), true);
    }

    private static void toggleFreelook(MinecraftClient client, KeyBinding binding) {
        drain(binding);
        FreelookController.toggle(client);
        client.player.sendMessage(Text.literal("Freelook " + (FreelookController.isActive() ? "on" : "off")), true);
    }

    private static void drain(KeyBinding binding) {
        while (binding.wasPressed()) {
            // Prevent END_CLIENT_TICK from toggling the mode a second time.
        }
    }
}
