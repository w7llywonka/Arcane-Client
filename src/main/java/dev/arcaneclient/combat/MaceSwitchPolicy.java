package dev.arcaneclient.combat;

import java.util.List;
import java.util.Objects;

/** Pure falling/target-below gate for switching to a mace before a smash attack. */
public final class MaceSwitchPolicy {
    public record Context(
        double fallDistance,
        double verticalVelocity,
        double targetDrop,
        boolean gliding,
        boolean touchingWater,
        boolean climbing
    ) {
    }

    public record Settings(
        double minimumFallDistance,
        double maximumVerticalVelocity,
        double minimumTargetDrop,
        int minimumDurabilityPercent
    ) {
        public Settings {
            if (minimumFallDistance < 0.0 || minimumTargetDrop < 0.0) {
                throw new IllegalArgumentException("distance thresholds cannot be negative");
            }
            if (minimumDurabilityPercent < 0 || minimumDurabilityPercent > 100) {
                throw new IllegalArgumentException("minimumDurabilityPercent must be between 0 and 100");
            }
        }
    }

    public record Candidate(int slot, int durabilityPercent) {
        public Candidate {
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("slot must be in the hotbar");
            if (durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("durabilityPercent must be between 0 and 100");
            }
        }
    }

    private MaceSwitchPolicy() {
    }

    public static boolean shouldSwitch(Context context, Settings settings) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(settings, "settings");
        return !context.gliding()
            && !context.touchingWater()
            && !context.climbing()
            && context.fallDistance() >= settings.minimumFallDistance()
            && context.verticalVelocity() <= settings.maximumVerticalVelocity()
            && context.targetDrop() >= settings.minimumTargetDrop();
    }

    public static int choose(int currentSlot, List<Candidate> candidates, Settings settings) {
        Objects.requireNonNull(candidates, "candidates");
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.durabilityPercent() < settings.minimumDurabilityPercent()) continue;
            if (best == null || candidate.durabilityPercent() > best.durabilityPercent()) best = candidate;
        }
        return best == null ? currentSlot : best.slot();
    }
}
