package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$adjustFreecamSpeed(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (FreecamController.onScroll(vertical)) ci.cancel();
    }
}
