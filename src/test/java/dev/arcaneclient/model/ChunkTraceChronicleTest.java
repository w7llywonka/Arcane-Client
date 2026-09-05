package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChunkTraceChronicleTest {
    @Test
    void authoritativeEmptySnapshotClearsCurrentButKeepsBoundedHistory() {
        ChunkTrace trace = new ChunkTrace();
        trace.merge(snapshot(100L, ScanEvidence.staticSignal(
            SignalCategory.CULTIVATION, new BlockPosition(1, 64, 1),
            "aligned crop row", 100, EvidenceFamily.FARM_GEOMETRY
        )));
        assertTrue(trace.scoreAt(100L) > 0);

        trace.merge(ScanResult.builder().completeSnapshot(200L, 24, 24).build());

        assertEquals(0, trace.scoreAt(200L));
        assertTrue(trace.historicalRawMaximum(SignalCategory.CULTIVATION) >= 100);
        assertEquals(2, trace.intelligenceAt(200L).completeSamples());
    }

    @Test
    void chronicleIsCappedAndRepeatedObservationDoesNotDefeatSingleFamilyCap() {
        ChunkTrace trace = new ChunkTrace();
        for (int sample = 0; sample < 10; ++sample) {
            long tick = 100L + sample * 20L;
            trace.merge(snapshot(tick, ScanEvidence.staticSignal(
                SignalCategory.CULTIVATION, new BlockPosition(2, 64, 2),
                "rectangular crop plot", 1000, EvidenceFamily.FARM_GEOMETRY
            )));
        }

        ChunkIntel intel = trace.intelligenceAt(300L);
        assertEquals(6, intel.completeSamples());
        assertEquals(6, intel.observations());
        assertEquals(1, intel.independentFamilies());
        assertTrue(intel.confidence() <= 65);
        assertEquals(6, intel.evidencePoints().getFirst().observations());
    }

    @Test
    void staleStaticEvidenceExpiresFromScoreAndDeepAnchor() {
        ChunkTrace trace = new ChunkTrace();
        trace.merge(snapshot(0L, ScanEvidence.staticSignal(
            SignalCategory.INFRASTRUCTURE, new BlockPosition(3, 20, 3),
            "worked deepslate", 120, EvidenceFamily.EXCAVATION
        )));

        assertEquals(0, trace.scoreAt(36000L));
        assertFalse(trace.hasActiveEvidenceAtOrBelow(36000L, ignored -> true, true, 48));
        assertEquals(0, trace.intelligenceAt(36000L).freshness());
    }

    @Test
    void evidenceConstellationIsHardCapped() {
        ScanResult.Builder builder = ScanResult.builder();
        for (int index = 0; index < 90; ++index) {
            builder.addStatic(
                SignalCategory.PLACED_BLOCK,
                EvidenceFamily.PLAYER_PLACEMENT,
                new BlockPosition(index, 64, 0),
                "placed fingerprint " + index,
                20 + index
            );
        }
        ChunkTrace trace = new ChunkTrace();
        trace.merge(builder.completeSnapshot(40L, 24, 24).build());

        assertEquals(64, trace.intelligenceAt(40L).evidencePoints().size());
    }

    @Test
    void oneEvidenceFamilyCannotCreateCrossCategorySynergy() {
        ScanResult.Builder builder = ScanResult.builder()
            .addStatic(SignalCategory.INTERACTION, EvidenceFamily.AUTOMATION, new BlockPosition(1, 64, 1), "state change one", 100)
            .addStatic(SignalCategory.INFRASTRUCTURE, EvidenceFamily.AUTOMATION, new BlockPosition(2, 64, 1), "automation block event", 100);
        ChunkTrace trace = new ChunkTrace();
        trace.merge(builder.completeSnapshot(20L, 24, 24).build());

        assertEquals(0, trace.summarizeAt(20L).synergy());
        assertEquals(7, trace.summarizeLegacyAt(20L, ignored -> true, true, false).synergy());
    }

    @Test
    void legacyHybridKeepsStrongestStaticObservationAndDeepWeightsIt() {
        ChunkTrace trace = new ChunkTrace();
        trace.merge(snapshot(100L, ScanEvidence.staticSignal(
            SignalCategory.INFRASTRUCTURE, new BlockPosition(1, 20, 1),
            "worked deepslate", 100, EvidenceFamily.EXCAVATION
        )));
        int deepScore = trace.summarizeLegacyAt(100L, ignored -> true, true, true).score();

        trace.merge(ScanResult.builder().completeSnapshot(200L, 24, 24).build());

        assertEquals(0, trace.scoreAt(200L));
        assertEquals(deepScore, trace.summarizeLegacyAt(200L, ignored -> true, true, true).score());
        assertTrue(trace.legacyHasActiveEvidenceAtOrBelow(200L, ignored -> true, true, 48));
    }

    @Test
    void legacyDeepFocusRejectsSurfaceOnlyEvidence() {
        ChunkTrace trace = new ChunkTrace();
        trace.merge(snapshot(100L, ScanEvidence.staticSignal(
            SignalCategory.CULTIVATION, new BlockPosition(1, 80, 1),
            "aligned crop row", 100, EvidenceFamily.FARM_GEOMETRY
        )));

        assertEquals(0, trace.summarizeLegacyAt(100L, ignored -> true, true, true).score());
        assertTrue(trace.summarizeLegacyAt(100L, ignored -> true, true, false).score() > 0);
    }

    @Test
    void liveEvidenceCacheIsBoundedBeforeExport() {
        ScanResult.Builder builder = ScanResult.builder().observedAt(10L);
        for (int index = 0; index < 90; ++index) {
            builder.addLive(
                SignalCategory.LIVE_ACTIVITY,
                EvidenceFamily.HARVEST,
                new BlockPosition(index, 64, 0),
                "harvest event " + index,
                20 + index,
                10L,
                12000
            );
        }
        ChunkTrace trace = new ChunkTrace();
        trace.merge(builder.build(), 10L);

        assertEquals(64, trace.liveSignalCount());
        assertEquals(64, trace.intelligenceAt(10L).evidencePoints().size());
    }

    @Test
    void familyFilterSeparatesVisibleModulesFromBroadCategories() {
        ChunkTrace trace = new ChunkTrace();
        trace.merge(ScanResult.builder()
            .addStatic(SignalCategory.CULTIVATION, EvidenceFamily.FARM_GEOMETRY, new BlockPosition(1, 64, 1), "aligned crop row", 80)
            .addStatic(SignalCategory.CULTIVATION, EvidenceFamily.HARVEST, new BlockPosition(2, 64, 1), "harvest rhythm", 80)
            .addStatic(SignalCategory.LIGHT_LEAK, EvidenceFamily.LIGHTING, new BlockPosition(3, 32, 1), "concealed light", 80)
            .completeSnapshot(10L, 24, 24).build());

        ScoreSummary farmOnly = trace.summarizeEvidenceAt(10L, evidence -> evidence.family() == EvidenceFamily.FARM_GEOMETRY, true);
        assertTrue(farmOnly.reasons().contains("aligned crop row"));
        assertFalse(farmOnly.reasons().contains("harvest rhythm"));
        assertTrue(trace.intelligenceEvidenceAt(10L, evidence -> evidence.family() != EvidenceFamily.LIGHTING, true)
            .evidencePoints().stream().noneMatch(point -> point.family() == EvidenceFamily.LIGHTING));
        assertEquals(EvidenceFamily.MANAGED_HABITAT, EvidenceFamily.infer(SignalCategory.INFRASTRUCTURE, "player-created entities"));
        assertEquals(EvidenceFamily.AMETHYST_ACTIVITY, EvidenceFamily.infer(
            SignalCategory.NATURAL_GROWTH, "amethyst stage advanced while loaded"
        ));
        assertEquals(EvidenceFamily.EXCAVATION, EvidenceFamily.infer(
            SignalCategory.NATURAL_GROWTH, "stripped geode shell"
        ));
        assertEquals(EvidenceFamily.ACCESS_TRAIL, EvidenceFamily.infer(
            SignalCategory.PLACED_BLOCK, "descending cobbled-deepslate access trail"
        ));
    }

    private static ScanResult snapshot(long tick, ScanEvidence evidence) {
        return ScanResult.builder().add(evidence).completeSnapshot(tick, 24, 24).build();
    }
}
