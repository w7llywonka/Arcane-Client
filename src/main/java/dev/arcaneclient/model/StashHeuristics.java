package dev.arcaneclient.model;

import dev.arcaneclient.model.SignalCategory;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class StashHeuristics {
    private static final int DIRECT_SCORE = 78;
    private static final int CLUSTER_SCORE = 55;

    private StashHeuristics() {
    }

    public static boolean direct(int score, boolean deepAnchor, Map<SignalCategory, Integer> categories) {
        return deepAnchor && score >= 78 && StashHeuristics.independentCategories(categories) >= 3 && StashHeuristics.machinery(categories) >= 16 && StashHeuristics.humanActivity(categories) >= 10;
    }

    public static boolean clusterMember(int score, boolean deepAnchor, Map<SignalCategory, Integer> categories) {
        return deepAnchor && score >= 55 && StashHeuristics.independentCategories(categories) >= 2 && (StashHeuristics.machinery(categories) >= 8 || StashHeuristics.humanActivity(categories) >= 12);
    }

    public static boolean clusterPair(int firstScore, boolean firstDeepAnchor, Map<SignalCategory, Integer> firstCategories, int secondScore, boolean secondDeepAnchor, Map<SignalCategory, Integer> secondCategories) {
        return firstScore + secondScore >= 130 && StashHeuristics.clusterMember(firstScore, firstDeepAnchor, firstCategories) && StashHeuristics.clusterMember(secondScore, secondDeepAnchor, secondCategories) && StashHeuristics.machinery(firstCategories) + StashHeuristics.machinery(secondCategories) >= 16 && StashHeuristics.humanActivity(firstCategories) + StashHeuristics.humanActivity(secondCategories) >= 16;
    }

    private static int independentCategories(Map<SignalCategory, Integer> categories) {
        int count = 0;
        for (int score : categories.values()) {
            if (score < 4) continue;
            ++count;
        }
        return count;
    }

    private static int machinery(Map<SignalCategory, Integer> categories) {
        return categories.getOrDefault((Object)SignalCategory.INFRASTRUCTURE, 0) + categories.getOrDefault((Object)SignalCategory.BLOCK_ENTITY, 0);
    }

    private static int humanActivity(Map<SignalCategory, Integer> categories) {
        return categories.getOrDefault((Object)SignalCategory.PLACED_BLOCK, 0) + categories.getOrDefault((Object)SignalCategory.INTERACTION, 0) + categories.getOrDefault((Object)SignalCategory.LIVE_ACTIVITY, 0) + categories.getOrDefault((Object)SignalCategory.ENTITY, 0);
    }
}
