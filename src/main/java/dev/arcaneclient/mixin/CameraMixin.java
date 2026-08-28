package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents vanilla from interpolating detached camera angles against stale non-ticking entity state. */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F")
    )
    private float arcaneclient$useCurrentDetachedYaw(Entity entity, float tickProgress) {
        return ownsCamera(entity) ? entity.getYaw() : entity.getYaw(tickProgress);
    }

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F")
    )
    private float arcaneclient$useCurrentDetachedPitch(Entity entity, float tickProgress) {
        return ownsCamera(entity) ? entity.getPitch() : entity.getPitch(tickProgress);
    }

    private static boolean ownsCamera(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return entity == client.getCameraEntity()
            && (FreecamController.isActive() || FreelookController.isActive());
    }
}
