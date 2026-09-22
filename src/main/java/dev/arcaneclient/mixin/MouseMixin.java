package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.CameraToggleInput;
import dev.arcaneclient.input.InputTelemetry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Inject(method = "onButton", at = @At("TAIL"))
    private void arcaneclient$toggleCameraModeImmediately(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        InputTelemetry.event();
        CameraToggleInput.onMouse(Minecraft.getInstance(), action, input);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$adjustFreecamSpeed(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (FreecamController.onScroll(vertical)) ci.cancel();
    }
}
