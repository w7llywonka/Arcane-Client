package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the real local player visible while the detached Freecam entity is focused. */
@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Redirect(
        method = "fillEntityRenderStates",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;",
            ordinal = 3
        )
    )
    private Entity arcaneclient$renderLocalPlayerDuringFreecam(Camera camera) {
        if (FreecamController.isActive()) {
            Entity player = MinecraftClient.getInstance().player;
            if (player != null) return player;
        }
        return camera.getFocusedEntity();
    }
}
