package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.effects.FireworkVisualParticle;
import org.spongepowered.asm.mixin.Mixin;

/** The vanilla spark implementation is package-private; this marker avoids reflective names. */
@Mixin(targets = "net.minecraft.client.particle.FireworkParticles$SparkParticle")
public abstract class FireworkParticleMarkerMixin implements FireworkVisualParticle { }
