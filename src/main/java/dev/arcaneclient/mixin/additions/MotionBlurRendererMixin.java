package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.visual.MotionBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MotionBlurRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void arcane$prepareMotionBlur(CallbackInfo ci) {
        MotionBlur.beginFrame(Minecraft.getInstance().level != null);
    }

    // 26.3 still finishes the world before hands, overlays, crosshair and GUI,
    // but the graphics buffer moved into RenderPearl.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
        shift = At.Shift.AFTER))
    private void arcane$blurCompletedWorld(CallbackInfo ci) {
        MotionBlur.renderWorld();
    }

    @Inject(method = "resize", at = @At("HEAD"))
    private void arcane$resetBlurSize(int width, int height, CallbackInfo ci) {
        MotionBlur.reset();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void arcane$closeMotionBlur(CallbackInfo ci) {
        MotionBlur.close();
    }
}
