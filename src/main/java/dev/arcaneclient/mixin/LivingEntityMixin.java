package dev.arcaneclient.mixin;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getModifiedSwingDuration", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$customSwingDuration(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        Minecraft client = Minecraft.getInstance();
        if (ArcaneClient.config() != null && ArcaneClient.config().swingSpeed && (Object) this == client.player) {
            cir.setReturnValue(ArcaneClient.config().swingDuration);
        }
    }
}
