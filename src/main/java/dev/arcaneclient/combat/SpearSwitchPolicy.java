package dev.arcaneclient.combat;

import java.util.List;
import java.util.Objects;

/** 1.21.11 spear selection: jabs do no damage inside their minimum reach. */
public final class SpearSwitchPolicy {
    public record Candidate(int slot, int materialRank, int durabilityPercent) {
        public Candidate {
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("slot must be in the hotbar");
            if (durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("durabilityPercent must be between 0 and 100");
            }
        }
    }

    public record Settings(double minimumTargetDistance, double maximumTargetDistance, int minimumDurabilityPercent) {
        public Settings {
            if (!Double.isFinite(minimumTargetDistance) || minimumTargetDistance < 0.0) {
                throw new IllegalArgumentException("minimumTargetDistance must be finite and non-negative");
            }
            if (!Double.isFinite(maximumTargetDistance) || maximumTargetDistance < minimumTargetDistance) {
                throw new IllegalArgumentException("maximumTargetDistance must be >= minimumTargetDistance");
            }
            if (minimumDurabilityPercent < 0 || minimumDurabilityPercent > 100) {
                throw new IllegalArgumentException("minimumDurabilityPercent must be between 0 and 100");
            }
        }
    }

    private SpearSwitchPolicy() {
    }

    public static boolean isUsefulDistance(
        double targetDistance,
        double weaponMinimumRange,
        double weaponMaximumRange,
        Settings settings
    ) {
        if (!Double.isFinite(targetDistance)) return false;
        double lower = Math.max(settings.minimumTargetDistance(), weaponMinimumRange + 1.0e-3);
        double upper = Math.min(settings.maximumTargetDistance(), weaponMaximumRange);
        return targetDistance >= lower && targetDistance <= upper;
    }

    public static int choose(int currentSlot, List<Candidate> candidates, Settings settings) {
        Objects.requireNonNull(candidates, "candidates");
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.durabilityPercent() < settings.minimumDurabilityPercent()) continue;
            if (best == null
                || candidate.materialRank() > best.materialRank()
                || (candidate.materialRank() == best.materialRank()
                    && candidate.durabilityPercent() > best.durabilityPercent())) {
                best = candidate;
            }
        }
        return best == null ? currentSlot : best.slot();
    }
}
