package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
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
    private void arcaneclient$showAllLoadedBoxes(AABB box, CallbackInfoReturnable<Boolean> cir) {
        if (FreecamController.isActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void arcaneclient$showAllLoadedSections(BoundingBox box, CallbackInfoReturnable<Integer> cir) {
        if (FreecamController.isActive()) {
            cir.setReturnValue(FrustumIntersection.INSIDE);
        }
    }
}
