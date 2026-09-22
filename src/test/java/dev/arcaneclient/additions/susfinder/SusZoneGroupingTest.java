package dev.arcaneclient.additions.susfinder;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SusZoneGroupingTest {
    private static SusZoneGrouping.Candidate candidate(int x, int z, int score, boolean inferred) {
        return new SusZoneGrouping.Candidate(x, z, score, inferred);
    }

    @Test void nearbyChunksProduceOneActualCentroidAndKeepInferenceLabel() {
        var zones = SusZoneGrouping.group(List.of(candidate(0, 0, 60, false), candidate(1, 0, 70, true)), 32);
        assertEquals(1, zones.size());
        assertEquals(16, zones.getFirst().x()); assertEquals(8, zones.getFirst().z());
        assertEquals(70, zones.getFirst().score()); assertTrue(zones.getFirst().inferred());
        assertEquals(2, zones.getFirst().members().size());
    }

    @Test void completeLinkageAvoidsLongChainsAndZeroRadiusSeparatesChunks() {
        var candidates = List.of(candidate(0, 0, 80, false), candidate(1, 0, 75, false), candidate(2, 0, 70, false));
        assertEquals(2, SusZoneGrouping.group(candidates, 16).size());
        assertEquals(3, SusZoneGrouping.group(candidates, 0).size());
        assertEquals(1, SusZoneGrouping.group(candidates, 32).size());
    }

    @Test void groupingIsStableAcrossInputOrderAndNegativeCoordinates() {
        var a = candidate(-2, -1, 60, false); var b = candidate(-1, -1, 60, false);
        var forward = SusZoneGrouping.group(List.of(a, b), 16);
        assertEquals(forward, SusZoneGrouping.group(List.of(b, a), 16));
        assertEquals(-16, forward.getFirst().x()); assertEquals(-8, forward.getFirst().z());
        assertTrue(SusZoneGrouping.group(List.of(), 32).isEmpty());
    }
}
