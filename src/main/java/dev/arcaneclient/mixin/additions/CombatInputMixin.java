package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.combat.CombatAdditionsController;

import dev.arcaneclient.combat.CombatAutomationController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Intercepts only the input owned by an explicitly enabled, held-input module. */
@Mixin(value = Minecraft.class, priority = 1100)
public abstract class CombatInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$paceHeldAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft)(Object)this;
        if (CombatAdditionsController.controlsAttack(client)) {
            // Also safe if another HEAD injector already prepared a temporary weapon.
            CombatAutomationController.restoreAfterManualAttack(client);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$paceCombatUse(CallbackInfo ci) {
        if (CombatAdditionsController.controlsUse((Minecraft)(Object)this)) ci.cancel();
    }
}
