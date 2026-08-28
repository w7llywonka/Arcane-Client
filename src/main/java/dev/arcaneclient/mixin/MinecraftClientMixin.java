package dev.arcaneclient.mixin;

import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts Auto Tool ahead of both the initial click and continued block breaking. */
@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"))
    private void arcaneclient$prepareAutoToolForAttack(CallbackInfoReturnable<Boolean> cir) {
        AutoToolController.prepareForCrosshair((MinecraftClient) (Object) this, true);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"))
    private void arcaneclient$prepareAutoToolForBreaking(boolean breaking, CallbackInfo ci) {
        AutoToolController.prepareForCrosshair((MinecraftClient) (Object) this, breaking);
    }
}
