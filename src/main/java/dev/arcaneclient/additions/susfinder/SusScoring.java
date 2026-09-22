package dev.arcaneclient.additions.susfinder;

import java.util.EnumMap;
import java.util.Map;

/** Presence/density heuristics. Natural generation can produce every signal in this class. */
public final class SusScoring {
    public enum Family {
        AMETHYST(7, 70), KELP(2, 55), BAMBOO(3, 60), BERRIES(4, 55), VINES(1, 45), DRIPSTONE(3, 55);

        final int weight;
        final int cap;
        Family(int weight, int cap) { this.weight = weight; this.cap = cap; }
    }

    private SusScoring() { }

    /** Square-root density and independent caps keep huge natural patches from scaling without bound. */
    public static int contribution(Family family, int blocks) {
        return Math.min(family.cap, (int)Math.floor(family.weight * Math.sqrt(Math.max(0, blocks))));
    }

    public static Score score(Map<Family, Integer> counts, int inferredLightHints, SusChunkFinderConfig config) {
        var contributions = new EnumMap<Family, Integer>(Family.class);
        int total = 0;
        for (Family family : Family.values()) {
            if (!config.enabled(family)) continue;
            int points = contribution(family, counts.getOrDefault(family, 0));
            if (points > 0) { contributions.put(family, points); total += points; }
        }
        // Inference never impersonates observed amethyst. It is weaker and capped separately.
        int inferred = config.amethyst && config.inferredAmethystLight
            ? Math.clamp(inferredLightHints, 0, 6) * 3 : 0;
        return new Score(Math.min(100, total + inferred), inferred, Map.copyOf(contributions));
    }

    public record Score(int value, int inferredPoints, Map<Family, Integer> contributions) {
        public boolean inferred() { return inferredPoints > 0; }
    }
}
