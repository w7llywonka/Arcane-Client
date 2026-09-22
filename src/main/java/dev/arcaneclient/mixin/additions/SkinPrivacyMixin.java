package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.ArcaneClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local render privacy only: never changes accounts or downloads alternate skins. */
@Mixin(AbstractClientPlayer.class)
public abstract class SkinPrivacyMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void arcane$privateSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (ArcaneClient.config() != null && ArcaneClient.config().intelAdditions.skinProtect) {
            cir.setReturnValue(DefaultPlayerSkin.getDefaultSkin());
        }
    }
}
