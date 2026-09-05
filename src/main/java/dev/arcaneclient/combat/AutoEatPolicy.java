package dev.arcaneclient.combat;

import java.util.List;
import java.util.Objects;

/** Pure food choice policy: fill useful hunger efficiently and never choose an unsafe candidate. */
public final class AutoEatPolicy {
    public record Candidate(int slot, int nutrition, float saturation, boolean safe) {
        public Candidate {
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("slot must be in the hotbar");
            if (nutrition < 0) throw new IllegalArgumentException("nutrition cannot be negative");
        }
    }

    private AutoEatPolicy() {
    }

    public static int choose(int foodLevel, List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        int missing = Math.max(0, 20 - Math.clamp(foodLevel, 0, 20));
        if (missing == 0) return -1;
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : candidates) {
            if (!candidate.safe() || candidate.nutrition() <= 0) continue;
            int useful = Math.min(missing, candidate.nutrition());
            int waste = Math.max(0, candidate.nutrition() - missing);
            double score = useful * 100.0 + candidate.saturation() * 10.0 - waste * 25.0;
            if (score > bestScore || score == bestScore && (best == null || candidate.slot() < best.slot())) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? -1 : best.slot();
    }
}
