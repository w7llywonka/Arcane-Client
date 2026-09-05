package dev.arcaneclient.combat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure armor upgrade scoring, independent from Minecraft inventory clicks. */
public final class AutoArmorPolicy {
    public enum Slot {
        HEAD,
        CHEST,
        LEGS,
        FEET
    }

    public record Candidate(
        int inventoryIndex,
        Slot slot,
        double armor,
        double toughness,
        double knockbackResistance,
        int protectionLevels,
        int specialistProtectionLevels,
        int durabilityPercent,
        boolean bindingCurse,
        boolean elytra
    ) {
        public Candidate {
            Objects.requireNonNull(slot, "slot");
            if (inventoryIndex < -1 || inventoryIndex >= 36) {
                throw new IllegalArgumentException("inventoryIndex must be -1 or a player inventory slot");
            }
            if (durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("durabilityPercent must be between 0 and 100");
            }
        }
    }

    public record Settings(
        int minimumDurabilityPercent,
        double minimumScoreImprovement,
        boolean allowBindingCurse,
        boolean allowElytra
    ) {
        public Settings {
            if (minimumDurabilityPercent < 0 || minimumDurabilityPercent > 100) {
                throw new IllegalArgumentException("minimumDurabilityPercent must be between 0 and 100");
            }
            if (minimumScoreImprovement < 0.0) {
                throw new IllegalArgumentException("minimumScoreImprovement cannot be negative");
            }
        }
    }

    private AutoArmorPolicy() {
    }

    public static double score(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return candidate.armor() * 100.0
            + candidate.toughness() * 32.0
            + candidate.knockbackResistance() * 80.0
            + candidate.protectionLevels() * 13.0
            + candidate.specialistProtectionLevels() * 4.0
            + candidate.durabilityPercent() * 0.08;
    }

    /** Chooses the single largest safe improvement so the controller performs one transaction per delay. */
    public static Optional<Candidate> chooseUpgrade(
        List<Candidate> inventory,
        List<Candidate> equipped,
        Settings settings
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(equipped, "equipped");
        Objects.requireNonNull(settings, "settings");

        Map<Slot, Double> equippedScores = new EnumMap<>(Slot.class);
        Map<Slot, Boolean> lockedSlots = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) equippedScores.put(slot, 0.0);
        for (Candidate candidate : equipped) {
            equippedScores.merge(candidate.slot(), score(candidate), Math::max);
            if (candidate.bindingCurse()) lockedSlots.put(candidate.slot(), true);
            if (candidate.elytra() && !settings.allowElytra()) lockedSlots.put(candidate.slot(), true);
        }

        Candidate best = null;
        double bestImprovement = settings.minimumScoreImprovement();
        for (Candidate candidate : inventory) {
            if (candidate.inventoryIndex() < 0) continue;
            // Survival cannot remove an equipped Curse of Binding item. Avoid
            // repeatedly sending a transaction the server must reject.
            if (lockedSlots.getOrDefault(candidate.slot(), false)) continue;
            if (candidate.durabilityPercent() < settings.minimumDurabilityPercent()) continue;
            if (candidate.bindingCurse() && !settings.allowBindingCurse()) continue;
            if (candidate.elytra() && !settings.allowElytra()) continue;
            double improvement = score(candidate) - equippedScores.get(candidate.slot());
            if (improvement > bestImprovement) {
                best = candidate;
                bestImprovement = improvement;
            }
        }
        return Optional.ofNullable(best);
    }
}
