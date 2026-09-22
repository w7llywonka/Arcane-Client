package dev.arcaneclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$removeHurtCamera(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci) {
        if (ArcaneClient.config() != null && (ArcaneClient.config().noHurtCam || ArcaneClient.config().noRender)) ci.cancel();
    }

    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$fullbright(LivingEntity entity, float tickProgress, CallbackInfoReturnable<Float> cir) {
        Minecraft client = Minecraft.getInstance();
        if (ArcaneClient.config() != null && ArcaneClient.config().fullbright && entity == client.player) {
            cir.setReturnValue(1.0f);
        }
    }
}
