package dev.arcaneclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents a stale pre-26.3 keyboard value from crashing mouse capture. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Redirect(
        method = "setAll",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(I)Z"
        )
    )
    private static boolean arcaneclient$safelyReadKeyboardState(int key) {
        try {
            return InputConstants.isKeyDown(key);
        } catch (IndexOutOfBoundsException ignored) {
            return false;
        }
    }
}
