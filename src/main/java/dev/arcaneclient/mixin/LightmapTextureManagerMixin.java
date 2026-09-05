package dev.arcaneclient.mixin;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.render.FullbrightPolicy;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes the client lightmap use vanilla's night-vision path without adding a gameplay effect. */
@Environment(EnvType.CLIENT)
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @Redirect(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"
        )
    )
    private boolean arcaneclient$fullbrightEffect(
        ClientPlayerEntity player,
        RegistryEntry<StatusEffect> effect
    ) {
        boolean enabled = ArcaneClient.config() != null && ArcaneClient.config().fullbright;
        return FullbrightPolicy.lightmapSeesEffect(enabled, effect.equals(StatusEffects.NIGHT_VISION), player.hasStatusEffect(effect));
    }
}
