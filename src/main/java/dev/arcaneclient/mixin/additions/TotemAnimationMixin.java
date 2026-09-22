package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.effects.Effects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional visual replacement only. The original item timer continues normally. */
@Mixin(LocalPlayer.class)
public abstract class TotemAnimationMixin {
    @Inject(method = "displayItemActivation", at = @At("TAIL"))
    private void arcaneclient$startTotemAnimation(ItemStack item, CallbackInfo ci) {
        Effects.startTotem(item);
    }

    @Inject(method = "resetItemActivation", at = @At("TAIL"))
    private void arcaneclient$clearTotemVisual(CallbackInfo ci) {
        Effects.clearTotem();
    }
}
