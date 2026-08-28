package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Client-only visual state that needs ticking rather than a render injection. */
@Environment(EnvType.CLIENT)
public final class VisualController {
    private static StatusEffectInstance appliedFullbrightEffect;

    private VisualController() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            appliedFullbrightEffect = null;
            return;
        }
        if (ArcaneClient.config().fullbright) {
            if (!player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.NIGHT_VISION, 220, 0, false, false, false);
                player.addStatusEffect(effect);
                appliedFullbrightEffect = effect;
            }
        } else if (appliedFullbrightEffect != null) {
            if (player.getStatusEffect(StatusEffects.NIGHT_VISION) == appliedFullbrightEffect) {
                player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            appliedFullbrightEffect = null;
        }
    }
}
