package dev.arcaneclient.scan;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class EvidenceHeuristics {
    private EvidenceHeuristics() {
    }

    public static int synchronizedCropGrowth(int cropCount, int stagedCount, int dominantCount, int maxRun) {
        if (cropCount < 8 || stagedCount < 8 || maxRun < 4 || dominantCount * 100 < stagedCount * 65) {
            return 0;
        }
        return Math.min(100, 30 + dominantCount * 4);
    }

    public static int spawnerCollection(int pairs) {
        return pairs <= 0 ? 0 : Math.min(180, 100 + pairs * 20);
    }

    public static int automationNetwork(int hoppers, int storage, int crafters, int links) {
        if (hoppers <= 0 || storage + crafters <= 0 || links <= 0) {
            return 0;
        }
        if (crafters == 0 && hoppers + storage < 3) {
            return 0;
        }
        return Math.min(200, 55 + hoppers * 8 + storage * 6 + crafters * 24 + links * 10);
    }

    public static int flowingWaterCollection(int waterBlocks, int maxRun, int linkedWater, int linkedCollectors, int linkedStorage) {
        if (waterBlocks < 4 || maxRun < 4 || linkedWater < 3 || linkedCollectors <= 0) {
            return 0;
        }
        return Math.min(170, 45 + Math.min(16, waterBlocks) * 4 + Math.min(16, linkedWater) * 6 + Math.min(8, linkedCollectors) * 18 + Math.min(8, linkedStorage) * 5);
    }

    public static int stableLayoutChange(int changedSections, int totalCountDelta) {
        if (changedSections <= 0 || changedSections > 2 || totalCountDelta < 0 || totalCountDelta > 12) {
            return 0;
        }
        return Math.min(130, 45 + changedSections * 20 + totalCountDelta * 3);
    }

    public static int droppedItemCluster(int entityCount, int itemCount) {
        if (entityCount < 3 || itemCount < 12) {
            return 0;
        }
        return Math.min(130, 35 + entityCount * 5 + itemCount * 2);
    }

    public static int experienceCluster(int orbCount, int experienceValue) {
        if (orbCount < 2 || experienceValue < 8) {
            return 0;
        }
        return Math.min(120, 35 + orbCount * 6 + experienceValue * 2);
    }

    public static int tradedVillagers(int villagerCount, int totalLevels) {
        if (villagerCount <= 0 || totalLevels < villagerCount * 2) {
            return 0;
        }
        return Math.min(180, 70 + villagerCount * 15 + totalLevels * 5);
    }

    public static int standaloneLightUpdate(int touchedSections, int nonzeroBytes) {
        if (touchedSections <= 0 || nonzeroBytes < 0) {
            return 0;
        }
        return Math.min(100, 25 + touchedSections * 10 + Math.min(40, nonzeroBytes / 64));
    }

    public static int cultivatedKelpColumns(int longColumns, int alignedTops) {
        if (longColumns < 8 || alignedTops * 100 < longColumns * 60) {
            return 0;
        }
        return Math.min(180, 65 + longColumns * 7 + alignedTops * 3);
    }

    public static int cultivatedBerryPatch(int count, int maxRun, boolean rectangular, boolean nativeTaiga) {
        int required = nativeTaiga ? 12 : 6;
        if (count < required || maxRun < (nativeTaiga ? 6 : 4) || nativeTaiga && !rectangular) {
            return 0;
        }
        return Math.min(150, 42 + count * 5 + maxRun * 3 + (rectangular ? 25 : 0));
    }

    public static int strippedGeode(int amethystShell, int calcite, int smoothBasalt, int budding, int buds) {
        if (budding > 0 || amethystShell < 16 || calcite < 4 || smoothBasalt < 4 || buds > 2) {
            return 0;
        }
        return Math.min(180, 85 + Math.min(40, amethystShell) + calcite + smoothBasalt);
    }

    public static int regionalGrowthBoost(int centerGrowth, int supportingNeighbors, int strongestNeighborGrowth) {
        if (centerGrowth < 3 || supportingNeighbors <= 0 || strongestNeighborGrowth < 3) {
            return 0;
        }
        return Math.min(16, 3 + supportingNeighbors * 3 + strongestNeighborGrowth / 4);
    }
}
