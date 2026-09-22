package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.CameraToggleInput;
import dev.arcaneclient.input.InputTelemetry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes camera-mode keybind presses take effect in the input frame that received them. */
@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("TAIL"))
    private void arcaneclient$toggleCameraModeImmediately(long window, int action, KeyEvent input, CallbackInfo ci) {
        InputTelemetry.event();
        CameraToggleInput.onKey(Minecraft.getInstance(), action, input);
    }
}
