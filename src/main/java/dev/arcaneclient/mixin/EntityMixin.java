package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes vanilla mouse look to Arcane's detached camera without rotating the real player. */
@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$routeCameraLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if ((Object) this != client.player) return;
        if (FreecamController.changeLookDirection(cursorDeltaX, cursorDeltaY)
            || FreelookController.changeLookDirection(cursorDeltaX, cursorDeltaY)) {
            ci.cancel();
        }
    }
}
