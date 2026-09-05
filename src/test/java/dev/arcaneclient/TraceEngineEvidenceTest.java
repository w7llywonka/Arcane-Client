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
        assertTrue(engine.allowsEvidence(evidence(EvidenceFamily.GROWTH)));
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
            Map.of(SignalCategory.CULTIVATION, 12, SignalCategory.NATURAL_GROWTH, 5)
        );
        assertEquals(0, TraceEngine.growthScore(liveOnly));
        assertEquals(17, TraceEngine.growthScore(growth));
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
