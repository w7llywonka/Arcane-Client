package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class VisualFovMixin {
    @Redirect(method = "calculateFov", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object arcaneclient$customWorldFov(OptionInstance<?> option) {
        // The first getValue reads the world FOV option. Substitute only that
        // render-time value so vanilla effects and the existing RETURN Zoom
        // hook operate normally, without touching the user's saved options.
        if (ArcaneClient.config() != null && ArcaneClient.config().visualAdditions.customFov) {
            return Math.clamp(ArcaneClient.config().visualAdditions.fov, 30, 140);
        }
        return option.get();
    }
}
