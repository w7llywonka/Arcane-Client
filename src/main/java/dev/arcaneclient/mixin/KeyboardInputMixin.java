package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={KeyboardInput.class})
public abstract class KeyboardInputMixin
extends Input {
    @Inject(method={"tick"}, at={@At(value="TAIL")})
    private void arcaneclient$freezePlayerDuringFreecam(CallbackInfo ci) {
        if (FreecamController.isActive()) {
            this.playerInput = PlayerInput.DEFAULT;
            this.movementVector = Vec2f.ZERO;
        }
    }
}
