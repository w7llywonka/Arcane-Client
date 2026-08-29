package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GrowthEvidenceHeuristicsTest {
    @Test
    void kelpNeedsManyLongSeaLevelAlignedColumns() {
        assertEquals(0, EvidenceHeuristics.cultivatedKelpColumns(7, 7));
        assertEquals(0, EvidenceHeuristics.cultivatedKelpColumns(10, 5));
        assertTrue(EvidenceHeuristics.cultivatedKelpColumns(10, 6) >= 100);
    }

    @Test
    void nativeBerryPatchesRequireFarmGeometry() {
        assertEquals(0, EvidenceHeuristics.cultivatedBerryPatch(12, 6, false, true));
        assertTrue(EvidenceHeuristics.cultivatedBerryPatch(12, 6, true, true) > 0);
        assertTrue(EvidenceHeuristics.cultivatedBerryPatch(6, 4, false, false) > 0);
    }

    @Test
    void intactGeodesAreSuppressed() {
        assertEquals(0, EvidenceHeuristics.strippedGeode(30, 8, 8, 1, 0));
        assertEquals(0, EvidenceHeuristics.strippedGeode(30, 8, 8, 0, 6));
        assertTrue(EvidenceHeuristics.strippedGeode(30, 8, 8, 0, 0) >= 100);
    }

    @Test
    void regionalSupportNeverCreatesEvidenceFromNothing() {
        assertEquals(0, EvidenceHeuristics.regionalGrowthBoost(0, 4, 16));
        assertEquals(0, EvidenceHeuristics.regionalGrowthBoost(8, 0, 0));
        assertTrue(EvidenceHeuristics.regionalGrowthBoost(8, 2, 12) > 0);
    }
}
