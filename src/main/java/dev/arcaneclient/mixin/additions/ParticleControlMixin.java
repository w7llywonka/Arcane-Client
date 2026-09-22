package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.effects.Effects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filters visual allocation or enqueueing, never particle packets or game effects. */
@Mixin(ParticleEngine.class)
public abstract class ParticleControlMixin {
    @Shadow protected ClientLevel level;
    @Unique private Particle arcaneclient$factoryBlockParticle;

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"), cancellable = true)
    private void arcaneclient$limitParticle(ParticleOptions effect, double x, double y, double z,
                                           double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        if (!Effects.allowParticle(effect, level)) cir.setReturnValue(null);
    }

    @Inject(method = "makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"))
    private void arcaneclient$rememberAdmittedBlockParticle(ParticleOptions effect, double x, double y, double z,
                                                          double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        // createParticle is private and its sole vanilla caller immediately enqueues the result.
        // Identity tracking avoids a second density/budget decision for factory-created debris.
        arcaneclient$factoryBlockParticle = particle instanceof TerrainParticle ? particle : null;
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$limitDirectParticle(Particle particle, CallbackInfo ci) {
        boolean factoryAdmitted = particle != null && particle == arcaneclient$factoryBlockParticle;
        arcaneclient$factoryBlockParticle = null;
        if (!Effects.allowParticleInstance(particle, level, factoryAdmitted)) ci.cancel();
    }

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("HEAD"))
    private void arcaneclient$clearFactoryIdentity(ClientLevel world, CallbackInfo ci) {
        arcaneclient$factoryBlockParticle = null;
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$disableEmitter(Entity entity, ParticleOptions effect, CallbackInfo ci) {
        if (!Effects.allowEmitter(effect, level)) ci.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$disableTimedEmitter(Entity entity, ParticleOptions effect, int lifetime, CallbackInfo ci) {
        if (!Effects.allowEmitter(effect, level)) ci.cancel();
    }
}
