package dev.arcaneclient.additions.effects;

/** Opt-in client cosmetics; vanilla animation and particles are unchanged when disabled. */
public final class EffectsConfig {
    public boolean totemAnimation;
    public int totemStyle;
    public int totemDuration = 24;
    public int totemSize = 112;
    public int totemOffsetX;
    public int totemOffsetY;
    public boolean particleControl;
    public int particlesPerTick = 256;
    public int totemDensity = 100;
    public int explosionDensity = 100;
    public int smokeDensity = 100;
    public int potionDensity = 100;
    public int blockDensity = 100;
    public int hitDensity = 100;
    public int poplarDensity = 100;
    public boolean adaptiveParticleBudget = true;
    public int particleTargetFps = 90;

    public void sanitize() {
        totemStyle = Math.clamp(totemStyle, 0, 2);
        totemDuration = Math.clamp(totemDuration, 6, 80);
        totemSize = Math.clamp(totemSize, 32, 220);
        totemOffsetX = Math.clamp(totemOffsetX, -300, 300);
        totemOffsetY = Math.clamp(totemOffsetY, -200, 200);
        particlesPerTick = Math.clamp(particlesPerTick, 16, 2048);
        totemDensity = Math.clamp(totemDensity, 0, 100);
        explosionDensity = Math.clamp(explosionDensity, 0, 100);
        smokeDensity = Math.clamp(smokeDensity, 0, 100);
        potionDensity = Math.clamp(potionDensity, 0, 100);
        blockDensity = Math.clamp(blockDensity, 0, 100);
        hitDensity = Math.clamp(hitDensity, 0, 100);
        poplarDensity = Math.clamp(poplarDensity, 0, 100);
        particleTargetFps = Math.clamp(particleTargetFps, 30, 240);
    }

    public int density(ParticleControlPolicy.Category category) {
        return switch (category) {
            case TOTEM -> totemDensity;
            case EXPLOSION -> explosionDensity;
            case SMOKE -> smokeDensity;
            case POTION -> potionDensity;
            case BLOCK -> blockDensity;
            case HIT -> hitDensity;
            case POPLAR -> poplarDensity;
        };
    }
}
