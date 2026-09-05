package dev.arcaneclient.combat;

import java.util.List;
import java.util.Objects;

/** Pure hotbar weapon scoring used by manual attacks and Trigger Bot. */
public final class SmartWeaponPolicy {
    public enum Mode {
        HIT_DAMAGE,
        SUSTAINED_DAMAGE
    }

    public record Candidate(
        int slot,
        double attackDamage,
        double attackSpeed,
        double enchantmentDamage,
        double armorPenetration,
        boolean shieldBreaker,
        int durabilityPercent,
        boolean eligible
    ) {
        public Candidate {
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("slot must be in the hotbar");
            if (durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("durabilityPercent must be between 0 and 100");
            }
        }
    }

    public record Target(double armor, boolean blocking) {
        public Target {
            if (armor < 0.0) throw new IllegalArgumentException("armor cannot be negative");
        }
    }

    public record Settings(Mode mode, int minimumDurabilityPercent, boolean preferShieldBreaker) {
        public Settings {
            Objects.requireNonNull(mode, "mode");
            if (minimumDurabilityPercent < 0 || minimumDurabilityPercent > 100) {
                throw new IllegalArgumentException("minimumDurabilityPercent must be between 0 and 100");
            }
        }
    }

    private SmartWeaponPolicy() {
    }

    public static int choose(
        int currentSlot,
        List<Candidate> candidates,
        Target target,
        Settings settings
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(settings, "settings");

        int bestSlot = currentSlot;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int offset = 0; offset < 9; offset++) {
            int slot = (currentSlot + offset) % 9;
            for (Candidate candidate : candidates) {
                if (candidate.slot() != slot || !candidate.eligible()) continue;
                if (candidate.durabilityPercent() < settings.minimumDurabilityPercent()) continue;
                double score = score(candidate, target, settings);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = slot;
                }
            }
        }
        return bestSlot;
    }

    public static double score(Candidate candidate, Target target, Settings settings) {
        double effectiveHit = candidate.attackDamage() + candidate.enchantmentDamage();
        effectiveHit += candidate.armorPenetration() * Math.min(20.0, target.armor()) * 0.08;
        if (target.blocking() && settings.preferShieldBreaker() && candidate.shieldBreaker()) effectiveHit += 8.0;
        if (settings.mode() == Mode.SUSTAINED_DAMAGE) {
            return effectiveHit * Math.max(0.1, candidate.attackSpeed());
        }
        return effectiveHit + Math.max(0.0, candidate.attackSpeed()) * 0.12;
    }
}
