package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels unsafe detached-camera interactions and puts Auto Tool ahead of normal mining. */
@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardAttackAndPrepareAutoTool(CallbackInfoReturnable<Boolean> cir) {
        if (DetachedCameraInteraction.isActive()) {
            cir.setReturnValue(false);
            return;
        }
        AutoToolController.prepareForCrosshair((MinecraftClient) (Object) this, true);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardBreakingAndPrepareAutoTool(boolean breaking, CallbackInfo ci) {
        if (DetachedCameraInteraction.isActive()) {
            ci.cancel();
            return;
        }
        AutoToolController.prepareForCrosshair((MinecraftClient) (Object) this, breaking);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardDetachedCameraItemUse(CallbackInfo ci) {
        if (DetachedCameraInteraction.isActive()) ci.cancel();
    }
}
