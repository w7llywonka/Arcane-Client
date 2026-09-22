package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents vanilla from interpolating detached camera angles against stale non-ticking entity state. */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraMixin {
    private double arcaneclient$cushionY = Double.NaN;
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$allowFreelookThroughWalls(
        float desiredDistance,
        CallbackInfoReturnable<Float> cir
    ) {
        if (FreelookController.ignoresCameraCollision()) {
            cir.setReturnValue(desiredDistance);
        }
    }

    @Redirect(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F")
    )
    private float arcaneclient$useCurrentDetachedYaw(Entity entity, float tickProgress) {
        if (ownsPlayerCamera(entity)) {
            if (FreecamController.isActive()) return FreecamController.cameraYaw();
            if (FreelookController.isActive()) return FreelookController.cameraYaw();
        }
        return entity.getViewYRot(tickProgress);
    }

    @Redirect(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F")
    )
    private float arcaneclient$useCurrentDetachedPitch(Entity entity, float tickProgress) {
        if (ownsPlayerCamera(entity)) {
            if (FreecamController.isActive()) return FreecamController.cameraPitch();
            if (FreelookController.isActive()) return FreelookController.cameraPitch();
        }
        return entity.getViewXRot(tickProgress);
    }

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void arcaneclient$applyFreecamPosition(
        float tickProgress,
        CallbackInfo ci
    ) {
        Entity focusedEntity = Minecraft.getInstance().getCameraEntity();
        if (!FreecamController.isActive() || !ownsPlayerCamera(focusedEntity)) return;
        setRotation(FreecamController.cameraYaw(), FreecamController.cameraPitch());
        setPosition(FreecamController.cameraPosition(tickProgress));
    }

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void arcaneclient$smoothCushionCamera(float tickProgress, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (dev.arcaneclient.ArcaneClient.config() == null
            || !dev.arcaneclient.ArcaneClient.config().visualAdditions.cushionCamera
            || client.player == null || FreecamController.isActive()) {
            arcaneclient$cushionY = Double.NaN;
            return;
        }
        Entity vehicle = client.player.getVehicle();
        if (vehicle == null || !net.minecraft.world.entity.EntityType.getKey(vehicle.getType()).getPath().contains("cushion")) {
            arcaneclient$cushionY = Double.NaN;
            return;
        }
        Vec3 current = ((Camera)(Object)this).position();
        arcaneclient$cushionY = Double.isNaN(arcaneclient$cushionY) ? current.y
            : arcaneclient$cushionY + (current.y - arcaneclient$cushionY) * 0.28;
        setPosition(new Vec3(current.x, arcaneclient$cushionY, current.z));
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void arcaneclient$heldZoom(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (dev.arcaneclient.ArcaneClient.config() == null || dev.arcaneclient.ArcaneClient.keybinds() == null) return;
        if (dev.arcaneclient.ArcaneClient.config().zoom && dev.arcaneclient.ArcaneClient.keybinds().zoom().isDown()) {
            cir.setReturnValue(cir.getReturnValue() * dev.arcaneclient.ArcaneClient.config().zoomPercent / 100.0F);
        }
    }

    private static boolean ownsPlayerCamera(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        return entity == client.player && entity == client.getCameraEntity();
    }
}
