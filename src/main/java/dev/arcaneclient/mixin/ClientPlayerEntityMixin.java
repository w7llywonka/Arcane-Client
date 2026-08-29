package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreelookController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps vanilla movement and movement packets active while Freelook owns the rendered camera. */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "isCamera", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$treatPlayerAsCameraDuringFreelook(CallbackInfoReturnable<Boolean> cir) {
        if (FreelookController.isActive()) cir.setReturnValue(true);
    }
}