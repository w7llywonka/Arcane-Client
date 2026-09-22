package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.combat.CombatAutomationController;
import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes detached-camera interaction input safely and puts Auto Tool ahead of normal mining. */
@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Unique
    private HitResult arcaneclient$savedItemUseTarget;
    @Unique
    private boolean arcaneclient$retargetedItemUse;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardAttackAndPrepareAutoTool(CallbackInfoReturnable<Boolean> cir) {
        if (DetachedCameraInteraction.isActive()) {
            cir.setReturnValue(false);
            return;
        }
        Minecraft client = (Minecraft)(Object)this;
        if (dev.arcaneclient.additions.nuker.Nuker.shouldHandle(client)) {
            cir.setReturnValue(false);
            return;
        }
        // Release any mining-owned slot before selecting the combat weapon that must
        // still be active when vanilla sends the synchronous attack packet.
        AutoToolController.prepareForCrosshair(client, true);
        CombatAutomationController.prepareForManualAttack(client);
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void arcaneclient$restoreCombatWeapon(CallbackInfoReturnable<Boolean> cir) {
        CombatAutomationController.restoreAfterManualAttack((Minecraft)(Object)this);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardBreakingAndPrepareAutoTool(boolean breaking, CallbackInfo ci) {
        if (dev.arcaneclient.additions.nuker.Nuker.handleMining((Minecraft)(Object)this)) {
            ci.cancel();
            return;
        }
        if (DetachedCameraInteraction.isActive()) {
            ci.cancel();
            return;
        }
        AutoToolController.prepareForCrosshair((Minecraft) (Object) this, breaking);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void arcaneclient$guardDetachedCameraItemUse(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (!DetachedCameraInteraction.isActive()) {
            return;
        }
        this.arcaneclient$savedItemUseTarget = client.hitResult;
        this.arcaneclient$retargetedItemUse = true;
        client.hitResult = DetachedCameraInteraction.itemUseTarget(client);
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void arcaneclient$restoreDetachedCameraItemTarget(CallbackInfo ci) {
        if (!this.arcaneclient$retargetedItemUse) {
            return;
        }
        ((Minecraft) (Object) this).hitResult = this.arcaneclient$savedItemUseTarget;
        this.arcaneclient$savedItemUseTarget = null;
        this.arcaneclient$retargetedItemUse = false;
    }
}
