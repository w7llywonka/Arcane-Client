package dev.arcaneclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.SignalCategory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class TraceEngineEvidenceTest {
    @Test
    void displayOnlyObservationsNeverEnterRawChunkScore() {
        TraceEngine engine = new TraceEngine(new ArcaneConfig());
        assertFalse(engine.allowsEvidence(evidence(EvidenceFamily.AMETHYST_ACTIVITY)));
        assertFalse(engine.allowsEvidence(evidence(EvidenceFamily.ACCESS_TRAIL)));
        assertFalse(engine.allowsEvidence(evidence(EvidenceFamily.GROWTH)));
        assertTrue(engine.allowsEvidence(growth()));
    }

    @Test
    void genericLiveActivityDoesNotMasqueradeAsRegionalGrowth() {
        TraceEngine.ChunkMarker liveOnly = new TraceEngine.ChunkMarker(
            0,
            0,
            20,
            List.of("active experience-orb cluster"),
            Map.of(SignalCategory.LIVE_ACTIVITY, 20)
        );
        TraceEngine.ChunkMarker growth = new TraceEngine.ChunkMarker(
            0,
            0,
            20,
            List.of("aligned crop row"),
            Map.of(SignalCategory.GROWN_PLANTS, 50)
        );
        assertEquals(0, TraceEngine.growthScore(liveOnly));
        assertEquals(50, TraceEngine.growthScore(growth));
    }

    @Test
    void restoredLegacyChannelsRemainIndependentlyConfigurable() {
        ArcaneConfig config = new ArcaneConfig();
        TraceEngine engine = new TraceEngine(config);
        config.playerBlockSignals = false;
        config.machineSignals = false;
        config.packetSignals = false;

        assertFalse(engine.allowsEvidence(evidence(SignalCategory.PLACED_BLOCK, EvidenceFamily.PLAYER_PLACEMENT)));
        assertFalse(engine.allowsEvidence(evidence(SignalCategory.INTERACTION, EvidenceFamily.OTHER)));
        assertFalse(engine.allowsEvidence(evidence(SignalCategory.INFRASTRUCTURE, EvidenceFamily.INFRASTRUCTURE)));
        assertFalse(engine.allowsEvidence(evidence(SignalCategory.BLOCK_ENTITY, EvidenceFamily.OTHER)));
        assertFalse(engine.allowsEvidence(evidence(SignalCategory.LIVE_ACTIVITY, EvidenceFamily.OTHER)));
    }

    private static dev.arcaneclient.model.ScanEvidence evidence(EvidenceFamily family) {
        return evidence(SignalCategory.NATURAL_GROWTH, family);
    }

    @Test
    void oldConfigCannotReenableContainersSoundsOrStaticPlants() {
        ArcaneConfig config = new ArcaneConfig();
        config.machineSignals = config.playerBlockSignals = config.packetSignals = true;
        TraceEngine engine = new TraceEngine(config);
        for (SignalCategory category : SignalCategory.values()) {
            if (category == SignalCategory.GROWTH_ACTIVITY || category == SignalCategory.PLANT_HARVEST) continue;
            for (EvidenceFamily family : EvidenceFamily.values()) {
                assertFalse(engine.allowsEvidence(dev.arcaneclient.model.ScanEvidence.liveSignal(
                    category, new BlockPosition(0, -30, 0), "growth harvest chest sound", 200, 0, 12000, family)));
            }
        }
        config.growthChronicle = false;
        assertTrue(engine.allowsEvidence(growth()), "Old live-growth switches cannot disable loaded plant counts");
    }

    @Test
    void freshEmptySnapshotRemovesOldPlantEvidence() {
        var trace = new dev.arcaneclient.model.ChunkTrace();
        trace.merge(dev.arcaneclient.model.ScanResult.builder().add(growth()).completeSnapshot(0, 24, 24).build());
        TraceEngine engine = new TraceEngine(new ArcaneConfig());
        assertTrue(trace.summarizeEvidenceAt(0, engine::allowsEvidence, true).score() > 0);
        trace.merge(dev.arcaneclient.model.ScanResult.builder().completeSnapshot(1, 24, 24).build());
        assertEquals(0, trace.summarizeEvidenceAt(1, engine::allowsEvidence, true).score());
    }

    private static dev.arcaneclient.model.ScanEvidence growth() {
        return dev.arcaneclient.model.ScanEvidence.staticSignal(SignalCategory.GROWN_PLANTS,
            new BlockPosition(0, 70, 0), "grown wheat", 1, EvidenceFamily.GROWTH);
    }

    private static dev.arcaneclient.model.ScanEvidence evidence(SignalCategory category, EvidenceFamily family) {
        return dev.arcaneclient.model.ScanEvidence.staticSignal(
            category,
            new BlockPosition(0, 64, 0),
            "test",
            100,
            family
        );
    }
}
