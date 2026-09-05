package dev.arcaneclient.model;

import dev.arcaneclient.model.SignalCategory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record ScoreSummary(int score, int synergy, Map<SignalCategory, Integer> categoryBreakdown, List<String> reasons) {
    public static final int DEFAULT_SUSPICIOUS_THRESHOLD = 35;
    private static final int SATURATION_HALF_STRENGTH = 20;
    private static final int SYNERGY_MINIMUM_CATEGORY_SCORE = 4;
    private static final int SYNERGY_PER_EXTRA_CATEGORY = 7;
    private static final int SYNERGY_CAP = 21;

    public ScoreSummary {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        if (synergy < 0) {
            throw new IllegalArgumentException("synergy must not be negative");
        }
        EnumMap<SignalCategory, Integer> breakdownCopy = new EnumMap<SignalCategory, Integer>(SignalCategory.class);
        for (SignalCategory category : SignalCategory.values()) {
            int value = categoryBreakdown.getOrDefault((Object)category, 0);
            if (value < 0 || value > category.cap()) {
                throw new IllegalArgumentException("category score exceeds its cap: " + String.valueOf((Object)category));
            }
            breakdownCopy.put(category, value);
        }
        categoryBreakdown = Collections.unmodifiableMap(breakdownCopy);
        reasons = List.copyOf(reasons);
    }

    public boolean suspicious() {
        return this.score >= 35;
    }

    static ScoreSummary fromRawStrengths(Map<SignalCategory, Integer> rawStrengths, Collection<String> contributingReasons) {
        return fromRawStrengths(rawStrengths, contributingReasons, Integer.MAX_VALUE);
    }

    static ScoreSummary fromRawStrengths(
        Map<SignalCategory, Integer> rawStrengths,
        Collection<String> contributingReasons,
        int independentFamilies
    ) {
        EnumMap<SignalCategory, Integer> breakdown = new EnumMap<SignalCategory, Integer>(SignalCategory.class);
        int baseScore = 0;
        int independentCategories = 0;
        for (SignalCategory category : SignalCategory.values()) {
            int categoryScore = ScoreSummary.saturate(rawStrengths.getOrDefault((Object)category, 0), category.cap());
            breakdown.put(category, categoryScore);
            baseScore += categoryScore;
            if (categoryScore < 4) continue;
            ++independentCategories;
        }
        int corroborated = Math.min(independentCategories, Math.max(0, independentFamilies));
        int synergy = Math.min(21, Math.max(0, corroborated - 1) * 7);
        int finalScore = Math.min(100, baseScore + synergy);
        TreeSet<String> sortedReasons = new TreeSet<String>(contributingReasons);
        return new ScoreSummary(finalScore, synergy, breakdown, new ArrayList<String>(sortedReasons));
    }

    private static int saturate(int rawStrength, int cap) {
        if (rawStrength <= 0) {
            return 0;
        }
        long score = (long)cap * (long)rawStrength / ((long)rawStrength + 20L);
        return (int)Math.min((long)cap, score);
    }
}
