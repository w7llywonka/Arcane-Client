package dev.arcaneclient.scan;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class EvidenceHeuristics {
    private EvidenceHeuristics() {
    }

    public static int organizedField(
        int cropCount,
        int farmlandSupport,
        int maxRun,
        boolean rectangular,
        int fillPercent
    ) {
        if (cropCount < 6 || maxRun < 4 || (!rectangular && maxRun < 6)) {
            return 0;
        }
        int layoutBonus = rectangular ? Math.min(24, Math.max(0, fillPercent - 50)) : 0;
        return Math.min(170, 24 + Math.min(40, cropCount * 3) + Math.min(24, farmlandSupport * 2) + layoutBonus);
    }

    public static int synchronizedCropGrowth(int cropCount, int stagedCount, int dominantCount, int maxRun) {
        if (cropCount < 8 || stagedCount < 8 || maxRun < 4 || dominantCount * 100 < stagedCount * 65) {
            return 0;
        }
        return Math.min(130, 24 + dominantCount * 4);
    }

    public static int matureCropConcentration(int cropCount, int matureCount, int maxRun) {
        if (cropCount < 8 || matureCount < 6 || maxRun < 4 || matureCount * 100 < cropCount * 50) {
            return 0;
        }
        return Math.min(120, 20 + matureCount * 4);
    }

    public static int organizedSaplings(int count, int maxRun, boolean rectangular) {
        if (count < 4 || maxRun < 3 || (!rectangular && maxRun < 4)) {
            return 0;
        }
        return Math.min(100, 20 + count * 6 + (rectangular ? 12 : 0));
    }

    public static int verticalGrowthColumns(
        int totalBlocks,
        int columns,
        int maximumHeight,
        int maxRun,
        boolean rectangular,
        int fillPercent
    ) {
        if (totalBlocks < 12 || columns < 6 || maximumHeight < 2) {
            return 0;
        }
        if (!rectangular && maxRun < 5) {
            return 0;
        }
        int layoutBonus = rectangular ? Math.min(20, Math.max(0, fillPercent - 50)) : 0;
        return Math.min(150, 25 + Math.min(48, totalBlocks * 2) + columns * 3 + layoutBonus);
    }

    public static int importedGrowthCluster(int count) {
        if (count < 3) {
            return 0;
        }
        return Math.min(110, 20 + count * 7);
    }

    public static int observedGrowth(int changedSections, int stageIncrease, int countIncrease) {
        int activity = stageIncrease + Math.max(0, countIncrease);
        if (changedSections <= 0 || changedSections > 4 || activity < 2) {
            return 0;
        }
        return Math.min(100, 18 + activity * 6 + changedSections * 3);
    }

    public static int observedHarvest(
        int changedSections,
        int stageDecrease,
        int matureDecrease,
        int countDecrease
    ) {
        int activity = stageDecrease + matureDecrease * 2 + Math.max(0, countDecrease) * 2;
        if (changedSections <= 0 || changedSections > 4 || activity < 3) {
            return 0;
        }
        return Math.min(140, 26 + activity * 7 + changedSections * 4);
    }
}
