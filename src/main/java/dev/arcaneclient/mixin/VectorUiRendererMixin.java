package dev.arcaneclient.mixin;

import dev.arcaneclient.screen.vector.VectorUi;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla GUI is deferred; native vector commands must run after its submission. */
@Mixin(GameRenderer.class)
public abstract class VectorUiRendererMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void arcane$beginUiFrame(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        VectorUi.startFrame();
    }

    @Inject(method = "render", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V",
        shift = At.Shift.AFTER
    ))
    private void arcane$finishUiFrame(CallbackInfo ci) {
        VectorUi.renderPending();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void arcane$closeUiRenderer(CallbackInfo ci) {
        VectorUi.close();
    }
}
