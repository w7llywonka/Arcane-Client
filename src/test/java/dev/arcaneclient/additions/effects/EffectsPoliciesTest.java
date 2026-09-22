package dev.arcaneclient.additions.effects;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EffectsPoliciesTest {
    @Test void defaultsPreserveVanilla() {
        EffectsConfig config = new EffectsConfig();
        assertFalse(config.totemAnimation);
        assertFalse(config.particleControl);
        for (var category : ParticleControlPolicy.Category.values()) assertEquals(100, config.density(category));
    }

    @Test void boundedAppearanceAndDensity() {
        EffectsConfig config = new EffectsConfig();
        config.totemStyle = -1;
        config.totemDuration = Integer.MAX_VALUE;
        config.totemSize = -1;
        config.totemOffsetX = Integer.MIN_VALUE;
        config.totemOffsetY = Integer.MAX_VALUE;
        config.particlesPerTick = Integer.MAX_VALUE;
        config.totemDensity = -100;
        config.explosionDensity = 1000;
        config.sanitize();
        assertEquals(0, config.totemStyle);
        assertEquals(80, config.totemDuration);
        assertEquals(32, config.totemSize);
        assertEquals(-300, config.totemOffsetX);
        assertEquals(200, config.totemOffsetY);
        assertEquals(2048, config.particlesPerTick);
        assertEquals(0, config.totemDensity);
        assertEquals(100, config.explosionDensity);
    }

    @Test void onlyExplicitVanillaCategoriesAreControlled() {
        assertEquals(ParticleControlPolicy.Category.TOTEM, ParticleControlPolicy.category("minecraft:totem_of_undying"));
        assertEquals(ParticleControlPolicy.Category.EXPLOSION, ParticleControlPolicy.category("minecraft:explosion_emitter"));
        assertEquals(ParticleControlPolicy.Category.SMOKE, ParticleControlPolicy.category("minecraft:campfire_signal_smoke"));
        assertEquals(ParticleControlPolicy.Category.POTION, ParticleControlPolicy.category("minecraft:entity_effect"));
        assertEquals(ParticleControlPolicy.Category.BLOCK, ParticleControlPolicy.category("minecraft:block"));
        assertEquals(ParticleControlPolicy.Category.HIT, ParticleControlPolicy.category("minecraft:crit"));
        assertEquals(ParticleControlPolicy.Category.POPLAR, ParticleControlPolicy.category("minecraft:red_poplar_leaves"));
        assertEquals(ParticleControlPolicy.Category.POPLAR, ParticleControlPolicy.category("minecraft:orange_poplar_leaves"));
        assertNull(ParticleControlPolicy.category("minecraft:portal"));
        assertNull(ParticleControlPolicy.category("anothermod:smoke"));
        assertNull(ParticleControlPolicy.category(null));
        assertTrue(ParticleControlPolicy.emitterCarrier("minecraft:explosion_emitter"));
        assertTrue(ParticleControlPolicy.emitterCarrier("minecraft:gust_emitter_small"));
        assertFalse(ParticleControlPolicy.emitterCarrier("minecraft:totem_of_undying"));
        assertFalse(ParticleControlPolicy.emitterCarrier("anothermod:explosion_emitter"));
    }

    @Test void densityIsDeterministicAndZeroDisables() {
        var budget = new ParticleControlPolicy.Budget();
        int accepted = 0;
        for (int i = 0; i < 100; i++) if (budget.allow(ParticleControlPolicy.Category.TOTEM, 25, 256, 1)) accepted++;
        assertEquals(25, accepted);
        for (int i = 0; i < 100; i++) assertFalse(budget.allow(ParticleControlPolicy.Category.SMOKE, 0, 256, 1));
        assertTrue(budget.allow(null, 0, 16, 1));
    }

    @Test void budgetIsSharedOnlyBySelectedCategoriesAndResetsPerTick() {
        var budget = new ParticleControlPolicy.Budget();
        for (int i = 0; i < 16; i++) assertTrue(budget.allow(ParticleControlPolicy.Category.TOTEM, 100, 16, 1));
        assertFalse(budget.allow(ParticleControlPolicy.Category.HIT, 100, 16, 1));
        assertTrue(budget.allow(null, 100, 16, 1));
        assertTrue(budget.allow(ParticleControlPolicy.Category.HIT, 100, 16, 2));
        budget.reset();
        assertTrue(budget.allow(ParticleControlPolicy.Category.HIT, 100, 16, 2));
    }

    @Test void animationsAreFiniteBoundedAndExpireWithoutResidualOpacity() {
        for (int style = 0; style < 3; style++) {
            assertEquals(0, TotemAnimationMath.frame(style, 0, 24).opacity());
            assertEquals(0, TotemAnimationMath.frame(style, 24, 24).opacity());
            assertEquals(0, TotemAnimationMath.frame(style, 100, 24).opacity());
            for (int quarterTick = 0; quarterTick <= 96; quarterTick++) {
                var frame = TotemAnimationMath.frame(style, quarterTick / 4.0f, 24);
                assertTrue(Float.isFinite(frame.scale()) && frame.scale() > 0 && frame.scale() <= 1);
                assertTrue(Float.isFinite(frame.rotation()));
                assertTrue(Float.isFinite(frame.slideY()) && frame.slideY() >= 0 && frame.slideY() <= 90);
                assertTrue(frame.opacity() >= 0 && frame.opacity() <= 1);
            }
        }
        assertTrue(Float.isFinite(TotemAnimationMath.frame(999, Float.NaN, -1).opacity()));
    }
}
