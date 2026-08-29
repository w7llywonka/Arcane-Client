package dev.arcaneclient.mixin;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$removeHurtCamera(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (ArcaneClient.config() != null && ArcaneClient.config().noHurtCam) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void arcaneclient$heldZoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (ArcaneClient.config() == null || ArcaneClient.keybinds() == null) return;
        if (ArcaneClient.config().zoom && ArcaneClient.keybinds().zoom().isPressed()) {
            cir.setReturnValue(cir.getReturnValue() * ArcaneClient.config().zoomPercent / 100.0f);
        }
    }

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$fullbright(LivingEntity entity, float tickProgress, CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ArcaneClient.config() != null && ArcaneClient.config().fullbright && entity == client.player) {
            cir.setReturnValue(1.0f);
        }
    }
}
