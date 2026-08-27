package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ChunkTrace;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.SignalCategory;
import org.junit.jupiter.api.Test;

final class GrowthOnlyScoringTest {
    private static final BlockPosition ORIGIN = new BlockPosition(0, 64, 0);

    @Test
    void storageLightEntitiesAndMachinesCannotScore() {
        ScanResult.Builder builder = ScanResult.builder();
        for (SignalCategory category : SignalCategory.values()) {
            if (category == SignalCategory.NATURAL_GROWTH || category == SignalCategory.CULTIVATION) {
                continue;
            }
            builder.addStatic(category, ORIGIN, "non-growth " + category.name(), Integer.MAX_VALUE);
        }

        ScanResult result = builder.build();
        assertEquals(0, result.score());
        assertFalse(result.suspicious());
        assertEquals(0, result.categoryBreakdown().get(SignalCategory.INFRASTRUCTURE));
        assertEquals(0, result.categoryBreakdown().get(SignalCategory.LIGHT_LEAK));
        assertEquals(0, result.categoryBreakdown().get(SignalCategory.BLOCK_ENTITY));
    }

    @Test
    void deliberateCultivationCrossesThresholdButNaturalGrowthAloneDoesNot() {
        ScanResult weak = ScanResult.builder()
            .addStatic(SignalCategory.CULTIVATION, ORIGIN, "too little cultivation", 23)
            .build();
        ScanResult deliberate = ScanResult.builder()
            .addStatic(SignalCategory.CULTIVATION, ORIGIN, "organized crop plot", 24)
            .build();
        ScanResult naturalOnly = ScanResult.builder()
            .addStatic(SignalCategory.NATURAL_GROWTH, ORIGIN, "natural growth", Integer.MAX_VALUE)
            .build();

        assertEquals(34, weak.score());
        assertFalse(weak.suspicious());
        assertEquals(35, deliberate.score());
        assertTrue(deliberate.suspicious());
        assertEquals(34, naturalOnly.score());
        assertFalse(naturalOnly.suspicious());
    }

    @Test
    void freshScansReplaceStaleCultivationEvidence() {
        ChunkTrace trace = new ChunkTrace();
        trace.mergeSnapshot(ScanResult.builder()
            .addStatic(SignalCategory.CULTIVATION, ORIGIN, "organized crops", 100)
            .build());
        assertTrue(trace.summarizeAt(0).suspicious());

        trace.mergeSnapshot(ScanResult.builder().build());
        assertEquals(0, trace.scoreAt(1));
    }

    @Test
    void configurationAllowsOnlyGrowthCategories() {
        ArcaneConfig config = new ArcaneConfig();

        assertTrue(config.allows(SignalCategory.NATURAL_GROWTH));
        assertTrue(config.allows(SignalCategory.CULTIVATION));
        assertFalse(config.allows(SignalCategory.INFRASTRUCTURE));
        assertFalse(config.allows(SignalCategory.LIGHT_LEAK));
        assertFalse(config.allows(SignalCategory.BLOCK_ENTITY));
        assertFalse(config.allows(SignalCategory.ENTITY));
    }

    @Test
    void organizedPatternsAreRequired() {
        assertEquals(0, EvidenceHeuristics.organizedField(5, 5, 5, true, 100));
        assertEquals(0, EvidenceHeuristics.organizedField(12, 0, 3, false, 30));
        assertTrue(EvidenceHeuristics.organizedField(12, 10, 6, true, 80) >= 60);

        assertEquals(0, EvidenceHeuristics.verticalGrowthColumns(10, 5, 3, 5, true, 80));
        assertTrue(EvidenceHeuristics.verticalGrowthColumns(18, 7, 3, 6, true, 85) >= 60);

        assertEquals(0, EvidenceHeuristics.importedGrowthCluster(2));
        assertTrue(EvidenceHeuristics.importedGrowthCluster(4) > 0);
    }

    @Test
    void temporalNoiseIsIgnoredUntilGrowthActivityRepeats() {
        assertEquals(0, EvidenceHeuristics.observedGrowth(1, 1, 0));
        assertTrue(EvidenceHeuristics.observedGrowth(1, 2, 0) > 0);

        assertEquals(0, EvidenceHeuristics.observedHarvest(1, 1, 0, 0));
        assertTrue(EvidenceHeuristics.observedHarvest(1, 1, 1, 0) > 0);
    }
}
