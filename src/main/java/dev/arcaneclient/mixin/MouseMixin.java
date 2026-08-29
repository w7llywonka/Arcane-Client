package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.CameraToggleInput;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("TAIL"))
    private void arcaneclient$toggleCameraModeImmediately(long window, MouseInput input, int action, CallbackInfo ci) {
        CameraToggleInput.onMouse(MinecraftClient.getInstance(), action, input);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$adjustFreecamSpeed(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (FreecamController.onScroll(vertical)) ci.cancel();
    }
}
