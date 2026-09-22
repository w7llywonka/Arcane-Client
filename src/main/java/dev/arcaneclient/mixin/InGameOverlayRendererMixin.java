package dev.arcaneclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arcaneclient.ArcaneClient;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Render groups the obstructive first-person overlays under one honest module. */
@Mixin(ScreenEffectRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$hideFire(PoseStack matrices, SubmitNodeCollector consumers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (noRender()) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$hideUnderwater(PlayerRenderState.WaterOverlay overlay, PoseStack matrices,
                                                     SubmitNodeCollector consumers, CallbackInfo ci) {
        if (noRender()) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void arcaneclient$hideInWall(Identifier atlasLocation, float u0, float v0, float u1, float v1,
                                                PoseStack matrices, SubmitNodeCollector consumers, int color, CallbackInfo ci) {
        if (noRender()) ci.cancel();
    }

    private static boolean noRender() {
        return ArcaneClient.config() != null && ArcaneClient.config().noRender;
    }
}
