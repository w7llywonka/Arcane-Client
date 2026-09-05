package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.combat.CombatAutomationController;
import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes detached-camera interaction input safely and puts Auto Tool ahead of normal mining. */
@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Unique
    private HitResult arcaneclient$savedItemUseTarget;
    @Unique
    private boolean arcaneclient$retargetedItemUse;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardAttackAndPrepareAutoTool(CallbackInfoReturnable<Boolean> cir) {
        if (DetachedCameraInteraction.isActive()) {
            cir.setReturnValue(false);
            return;
        }
        MinecraftClient client = (MinecraftClient)(Object)this;
        // Release any mining-owned slot before selecting the combat weapon that must
        // still be active when vanilla sends the synchronous attack packet.
        AutoToolController.prepareForCrosshair(client, true);
        CombatAutomationController.prepareForManualAttack(client);
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void arcaneclient$restoreCombatWeapon(CallbackInfoReturnable<Boolean> cir) {
        CombatAutomationController.restoreAfterManualAttack((MinecraftClient)(Object)this);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardBreakingAndPrepareAutoTool(boolean breaking, CallbackInfo ci) {
        if (DetachedCameraInteraction.isActive()) {
            ci.cancel();
            return;
        }
        AutoToolController.prepareForCrosshair((MinecraftClient) (Object) this, breaking);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void arcaneclient$guardDetachedCameraItemUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (!DetachedCameraInteraction.isActive()) {
            return;
        }
        this.arcaneclient$savedItemUseTarget = client.crosshairTarget;
        this.arcaneclient$retargetedItemUse = true;
        client.crosshairTarget = DetachedCameraInteraction.itemUseTarget(client);
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void arcaneclient$restoreDetachedCameraItemTarget(CallbackInfo ci) {
        if (!this.arcaneclient$retargetedItemUse) {
            return;
        }
        ((MinecraftClient) (Object) this).crosshairTarget = this.arcaneclient$savedItemUseTarget;
        this.arcaneclient$savedItemUseTarget = null;
        this.arcaneclient$retargetedItemUse = false;
    }
}
