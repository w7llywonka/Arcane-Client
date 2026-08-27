package dev.arcaneclient.model;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GrowthSiteHeuristics {
    private static final int DIRECT_SCORE = 48;
    private static final int CLUSTER_SCORE = 35;
    private static final int DIRECT_CULTIVATION = 35;
    private static final int CLUSTER_CULTIVATION = 24;

    private GrowthSiteHeuristics() {
    }

    public static boolean direct(int score, Map<SignalCategory, Integer> categories) {
        return score >= DIRECT_SCORE && cultivation(categories) >= DIRECT_CULTIVATION;
    }

    public static boolean clusterMember(int score, Map<SignalCategory, Integer> categories) {
        return score >= CLUSTER_SCORE && cultivation(categories) >= CLUSTER_CULTIVATION;
    }

    public static boolean clusterPair(
        int firstScore,
        Map<SignalCategory, Integer> firstCategories,
        int secondScore,
        Map<SignalCategory, Integer> secondCategories
    ) {
        return firstScore + secondScore >= 80
            && clusterMember(firstScore, firstCategories)
            && clusterMember(secondScore, secondCategories)
            && cultivation(firstCategories) + cultivation(secondCategories) >= 56;
    }

    private static int cultivation(Map<SignalCategory, Integer> categories) {
        return categories.getOrDefault(SignalCategory.CULTIVATION, 0);
    }
}
