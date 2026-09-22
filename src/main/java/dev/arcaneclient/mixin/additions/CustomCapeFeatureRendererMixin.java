package dev.arcaneclient.mixin.additions;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arcaneclient.additions.visual.CustomAccessories;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoids two capes occupying the local player's back while the cosmetic cape is selected. */
@Mixin(CapeLayer.class)
public abstract class CustomCapeFeatureRendererMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void arcane$localCape(PoseStack matrices, SubmitNodeCollector queue, int light,
                                  AvatarRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (CustomAccessories.replacesVanillaCape(state)) ci.cancel();
    }
}
