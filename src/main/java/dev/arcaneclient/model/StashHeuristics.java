package dev.arcaneclient.model;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Direct and neighboring-chunk promotion rules from the archived 1.6/1.8 engine. */
@Environment(EnvType.CLIENT)
public final class StashHeuristics {
    private StashHeuristics() {
    }

    public static boolean direct(int score, boolean deepAnchor, Map<SignalCategory, Integer> categories) {
        return deepAnchor
            && score >= 78
            && independentCategories(categories) >= 3
            && machinery(categories) >= 16
            && humanActivity(categories) >= 10;
    }

    public static boolean clusterMember(int score, boolean deepAnchor, Map<SignalCategory, Integer> categories) {
        return deepAnchor
            && score >= 55
            && independentCategories(categories) >= 2
            && (machinery(categories) >= 8 || humanActivity(categories) >= 12);
    }

    public static boolean clusterPair(
        int firstScore,
        boolean firstDeepAnchor,
        Map<SignalCategory, Integer> firstCategories,
        int secondScore,
        boolean secondDeepAnchor,
        Map<SignalCategory, Integer> secondCategories
    ) {
        return firstScore + secondScore >= 130
            && clusterMember(firstScore, firstDeepAnchor, firstCategories)
            && clusterMember(secondScore, secondDeepAnchor, secondCategories)
            && machinery(firstCategories) + machinery(secondCategories) >= 16
            && humanActivity(firstCategories) + humanActivity(secondCategories) >= 16;
    }

    public static int independentCategories(Map<SignalCategory, Integer> categories) {
        int count = 0;
        for (int score : categories.values()) {
            if (score >= 4) ++count;
        }
        return count;
    }

    private static int machinery(Map<SignalCategory, Integer> categories) {
        return categories.getOrDefault(SignalCategory.INFRASTRUCTURE, 0)
            + categories.getOrDefault(SignalCategory.BLOCK_ENTITY, 0);
    }

    private static int humanActivity(Map<SignalCategory, Integer> categories) {
        return categories.getOrDefault(SignalCategory.PLACED_BLOCK, 0)
            + categories.getOrDefault(SignalCategory.INTERACTION, 0)
            + categories.getOrDefault(SignalCategory.LIVE_ACTIVITY, 0)
            + categories.getOrDefault(SignalCategory.ENTITY, 0);
    }
}
