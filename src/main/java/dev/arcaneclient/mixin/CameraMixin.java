package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla from interpolating detached camera angles against stale non-ticking entity state. */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F")
    )
    private float arcaneclient$useCurrentDetachedYaw(Entity entity, float tickProgress) {
        if (ownsPlayerCamera(entity)) {
            if (FreecamController.isActive()) return FreecamController.cameraYaw();
            if (FreelookController.isActive()) return FreelookController.cameraYaw();
        }
        return entity.getYaw(tickProgress);
    }

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F")
    )
    private float arcaneclient$useCurrentDetachedPitch(Entity entity, float tickProgress) {
        if (ownsPlayerCamera(entity)) {
            if (FreecamController.isActive()) return FreecamController.cameraPitch();
            if (FreelookController.isActive()) return FreelookController.cameraPitch();
        }
        return entity.getPitch(tickProgress);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void arcaneclient$applyFreecamPosition(
        World area,
        Entity focusedEntity,
        boolean thirdPerson,
        boolean inverseView,
        float tickProgress,
        CallbackInfo ci
    ) {
        if (!FreecamController.isActive() || !ownsPlayerCamera(focusedEntity)) return;
        setRotation(FreecamController.cameraYaw(), FreecamController.cameraPitch());
        setPos(FreecamController.cameraPosition(tickProgress));
    }

    private static boolean ownsPlayerCamera(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return entity == client.player && entity == client.getCameraEntity();
    }
}
