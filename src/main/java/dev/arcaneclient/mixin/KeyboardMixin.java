package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.CameraToggleInput;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes camera-mode keybind presses take effect in the input frame that received them. */
@Environment(EnvType.CLIENT)
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("TAIL"))
    private void arcaneclient$toggleCameraModeImmediately(long window, int action, KeyInput input, CallbackInfo ci) {
        CameraToggleInput.onKey(MinecraftClient.getInstance(), action, input);
    }
}
