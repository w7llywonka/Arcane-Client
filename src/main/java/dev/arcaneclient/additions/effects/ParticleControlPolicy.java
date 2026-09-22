package dev.arcaneclient.additions.effects;

import java.util.Arrays;

/** Pure, deterministic admission policy. Unlisted/modded particle IDs always pass through. */
public final class ParticleControlPolicy {
    public enum Category { TOTEM, EXPLOSION, SMOKE, POTION, BLOCK, HIT, POPLAR }

    private ParticleControlPolicy() { }

    public static Category category(String id) {
        if (id == null || !id.startsWith("minecraft:")) return null;
        if (id.contains("poplar")) return Category.POPLAR;
        return switch (id.substring("minecraft:".length())) {
            case "totem_of_undying" -> Category.TOTEM;
            case "explosion", "explosion_emitter", "firework", "flash",
                 "gust", "small_gust", "gust_emitter_small", "gust_emitter_large" -> Category.EXPLOSION;
            case "smoke", "large_smoke", "white_smoke", "campfire_cosy_smoke",
                 "campfire_signal_smoke", "ash", "white_ash" -> Category.SMOKE;
            case "effect", "entity_effect", "instant_effect", "witch", "dragon_breath" -> Category.POTION;
            case "block", "block_marker", "falling_dust", "dust_pillar", "block_crumble" -> Category.BLOCK;
            case "crit", "enchanted_hit", "damage_indicator", "sweep_attack" -> Category.HIT;
            default -> null;
        };
    }

    /** Invisible particle emitters must not be density-thinned again before their children. */
    public static boolean emitterCarrier(String id) {
        return "minecraft:explosion_emitter".equals(id) || "minecraft:gust_emitter_small".equals(id)
            || "minecraft:gust_emitter_large".equals(id);
    }

    public static final class Budget {
        private final int[] remainder = new int[Category.values().length];
        private long tick = Long.MIN_VALUE;
        private int accepted;

        public boolean allow(Category category, int density, int limit, long currentTick) {
            if (category == null) return true;
            if (currentTick != tick) {
                tick = currentTick;
                accepted = 0;
            }
            int percent = Math.clamp(density, 0, 100);
            if (percent == 0) return false;
            int index = category.ordinal();
            remainder[index] += percent;
            if (remainder[index] < 100) return false;
            remainder[index] -= 100;
            if (accepted >= Math.clamp(limit, 16, 2048)) return false;
            accepted++;
            return true;
        }

        public void reset() {
            Arrays.fill(remainder, 0);
            tick = Long.MIN_VALUE;
            accepted = 0;
        }
    }
}
