package dev.arcaneclient.mixin;

import dev.arcaneclient.ArcaneClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render groups the obstructive first-person overlays under one honest module. */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$hideFire(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite, CallbackInfo ci) {
        if (noRender()) ci.cancel();
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$hideUnderwater(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers, CallbackInfo ci) {
        if (noRender()) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$hideInWall(Sprite sprite, MatrixStack matrices, VertexConsumerProvider consumers, CallbackInfo ci) {
        if (noRender()) ci.cancel();
    }

    private static boolean noRender() {
        return ArcaneClient.config() != null && ArcaneClient.config().noRender;
    }
}
