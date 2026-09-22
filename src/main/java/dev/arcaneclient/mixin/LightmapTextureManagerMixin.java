package dev.arcaneclient.mixin;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.render.FullbrightPolicy;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes the client lightmap use vanilla's night-vision path without adding a gameplay effect. */
@Environment(EnvType.CLIENT)
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapTextureManagerMixin {
    @Redirect(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"
        )
    )
    private boolean arcaneclient$fullbrightEffect(
        LocalPlayer player,
        Holder<MobEffect> effect
    ) {
        boolean enabled = ArcaneClient.config() != null && ArcaneClient.config().fullbright;
        return FullbrightPolicy.lightmapSeesEffect(enabled, effect.equals(MobEffects.NIGHT_VISION), player.hasEffect(effect));
    }
}
