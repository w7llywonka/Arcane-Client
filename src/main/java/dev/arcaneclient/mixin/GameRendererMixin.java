package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={GameRenderer.class})
public abstract class GameRendererMixin {
    @Inject(method={"renderHand"}, at={@At(value="HEAD")}, cancellable=true)
    private void arcaneclient$hideHandInFreecam(float tickProgress, boolean sleeping, Matrix4f viewMatrix, CallbackInfo ci) {
        if (FreecamController.isActive()) {
            ci.cancel();
        }
    }
}
