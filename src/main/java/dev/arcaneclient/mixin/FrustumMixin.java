package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Box;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps every client-loaded render section eligible while the camera is detached. */
@Environment(EnvType.CLIENT)
@Mixin(Frustum.class)
public abstract class FrustumMixin {
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$showAllLoadedBoxes(Box box, CallbackInfoReturnable<Boolean> cir) {
        if (FreecamController.isActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "intersectAab(Lnet/minecraft/util/math/BlockBox;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void arcaneclient$showAllLoadedSections(BlockBox box, CallbackInfoReturnable<Integer> cir) {
        if (FreecamController.isActive()) {
            cir.setReturnValue(FrustumIntersection.INSIDE);
        }
    }
}
