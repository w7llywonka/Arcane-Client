package dev.arcaneclient.utility;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure source/target planning for safe hotbar replenishment. */
public final class HotbarRefillPolicy {
    public record Target(int hotbarSlot, int count, int maximumCount, boolean selected, String itemKey) {
        public Target {
            if (hotbarSlot < 0 || hotbarSlot >= 9) throw new IllegalArgumentException("hotbarSlot must be in the hotbar");
            if (count < 0 || maximumCount < 1 || count > maximumCount) throw new IllegalArgumentException("invalid stack count");
            Objects.requireNonNull(itemKey, "itemKey");
        }
    }

    public record Source(int inventoryIndex, int count, String itemKey) {
        public Source {
            if (inventoryIndex < 9 || inventoryIndex >= 36) {
                throw new IllegalArgumentException("source must be in main inventory slots 9-35");
            }
            if (count < 1) throw new IllegalArgumentException("source count must be positive");
            Objects.requireNonNull(itemKey, "itemKey");
        }
    }

    public record Settings(int threshold, boolean refillSelectedSlot) {
        public Settings {
            if (threshold < 1) throw new IllegalArgumentException("threshold must be positive");
        }
    }

    public record Operation(int sourceInventoryIndex, int targetHotbarSlot) {
    }

    private HotbarRefillPolicy() {
    }

    public static Optional<Operation> choose(List<Target> targets, List<Source> sources, Settings settings) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(settings, "settings");
        Target bestTarget = null;
        Source bestSource = null;
        for (Target target : targets) {
            if (target.count() <= 0 || target.count() >= target.maximumCount()) continue;
            if (target.count() > Math.min(settings.threshold(), target.maximumCount() - 1)) continue;
            if (target.selected() && !settings.refillSelectedSlot()) continue;
            Source source = sources.stream()
                .filter(candidate -> candidate.itemKey().equals(target.itemKey()))
                .max(java.util.Comparator.comparingInt(Source::count))
                .orElse(null);
            if (source == null) continue;
            if (bestTarget == null || target.count() < bestTarget.count()) {
                bestTarget = target;
                bestSource = source;
            }
        }
        return bestTarget == null
            ? Optional.empty()
            : Optional.of(new Operation(bestSource.inventoryIndex(), bestTarget.hotbarSlot()));
    }
}
