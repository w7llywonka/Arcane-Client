package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.ScanEvidence;
import dev.arcaneclient.model.SignalCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EvidenceCorrelatorTest {
    private static final GrowthTransitions.GrowthEvent HARVEST = new GrowthTransitions.GrowthEvent(
        SignalCategory.LIVE_ACTIVITY, 125, "crop harvested", GrowthTransitions.Family.CROP
    );
    private static final GrowthTransitions.GrowthEvent GROWTH = new GrowthTransitions.GrowthEvent(
        SignalCategory.NATURAL_GROWTH, 18, "crop growth tick", GrowthTransitions.Family.CROP
    );

    @Test
    void emitsHarvestOnlyForCoordinatedAxisRun() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        List<ScanEvidence> output = List.of();
        for (int x = 0; x < 6; ++x) {
            output = correlator.record(new BlockPosition(x, 64, 0), x, HARVEST, false);
        }
        assertEquals(1, output.size());
        assertEquals(EvidenceFamily.HARVEST, output.getFirst().family());

        correlator.reset();
        for (int index = 0; index < 6; ++index) {
            output = correlator.record(new BlockPosition(index, 64 + index, index), index, HARVEST, false);
        }
        assertTrue(output.isEmpty());
    }

    @Test
    void emitsAutomationOnlyAfterRepeatedMultiPositionCadence() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        List<ScanEvidence> output = List.of();
        for (int index = 0; index < 5; ++index) {
            output = correlator.record(new BlockPosition(index % 3, 70, 0), index * 10L, null, true);
        }
        assertEquals(1, output.size());
        assertEquals(EvidenceFamily.AUTOMATION, output.getFirst().family());
    }

    @Test
    void passiveGrowthNeedsTimeAndRepeatedPositions() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        List<ScanEvidence> output = List.of();
        for (int index = 0; index < 4; ++index) {
            output = correlator.record(new BlockPosition(index % 2, 64, 0), index * 10L, GROWTH, false);
        }
        assertFalse(output.isEmpty());
        assertEquals(EvidenceFamily.GROWTH, output.getFirst().family());
    }

    @Test
    void trackedChunkStateIsBoundedAndResettable() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        for (int chunk = 0; chunk < EvidenceCorrelator.MAX_TRACKED_CHUNKS + 20; ++chunk) {
            correlator.record(new BlockPosition(chunk * 16, 64, 0), 1L, null, false);
        }
        assertEquals(EvidenceCorrelator.MAX_TRACKED_CHUNKS, correlator.trackedChunks());
        correlator.reset();
        assertEquals(0, correlator.trackedChunks());
    }
}
