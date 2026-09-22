package dev.arcaneclient.mixin.additions;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arcaneclient.additions.effects.Effects;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses only the vanilla activation model while Arcane's replacement is active. */
@Mixin(ScreenEffectRenderer.class)
public abstract class TotemOverlayMixin {
    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$replaceTotemVisual(PlayerRenderState playerState, PoseStack matrices,
                                                 float tickProgress, SubmitNodeCollector queue, CallbackInfo ci) {
        PlayerRenderState.ItemActivationRenderState activation = playerState.itemActivation;
        if (activation != null && Effects.replacesTotem(activation.item)) ci.cancel();
    }
}
