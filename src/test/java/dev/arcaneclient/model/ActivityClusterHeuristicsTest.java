package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ActivityClusterHeuristicsTest {
    @Test
    void adjacentModerateGrowthChunksCorroborateEachOther() {
        List<ActivityClusterHeuristics.Cluster> clusters = ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 48, 55, 2, mask(EvidenceFamily.GROWTH, EvidenceFamily.FARM_GEOMETRY), 18, 4),
            observation(1, 0, 44, 52, 2, mask(EvidenceFamily.GROWTH, EvidenceFamily.HARVEST), 16, 3)
        ), 8);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.getFirst().members());
        assertTrue(clusters.getFirst().confidence() >= 50);
        assertEquals(3, clusters.getFirst().families());
    }

    @Test
    void twoChunkGapBridgesOnlyAHighConfidenceSharedPrimaryFamily() {
        List<ActivityClusterHeuristics.Cluster> bridged = ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 50, 55, 2, mask(EvidenceFamily.GROWTH, EvidenceFamily.FARM_GEOMETRY), 18, 3),
            observation(2, 0, 48, 54, 2, mask(EvidenceFamily.GROWTH, EvidenceFamily.HARVEST), 16, 3)
        ), 8);
        List<ActivityClusterHeuristics.Cluster> unrelated = ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 50, 55, 2, mask(EvidenceFamily.GROWTH, EvidenceFamily.FARM_GEOMETRY), 18, 3),
            observation(2, 0, 48, 54, 2, mask(EvidenceFamily.AUTOMATION, EvidenceFamily.HARVEST), 16, 3)
        ), 8);

        assertEquals(1, bridged.size());
        assertTrue(unrelated.isEmpty());
    }

    @Test
    void supportOnlyOrWeakEvidenceCannotCreateACluster() {
        assertTrue(ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 80, 80, 4, mask(EvidenceFamily.INFRASTRUCTURE, EvidenceFamily.LIGHTING), 0, 60),
            observation(1, 0, 80, 80, 4, mask(EvidenceFamily.PLAYER_PLACEMENT, EvidenceFamily.LIGHTING), 0, 60)
        ), 8).isEmpty());
        assertTrue(ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 21, 90, 8, mask(EvidenceFamily.GROWTH, EvidenceFamily.HARVEST), 20, 20)
        ), 8).isEmpty());
    }

    @Test
    void amethystCannotSeedAClusterEvenWithPerfectSyntheticScores() {
        assertTrue(ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 100, 100, 20, mask(EvidenceFamily.AMETHYST_ACTIVITY), 100, 0)
        ), 8).isEmpty());
    }

    @Test
    void denseRepeatedSingleChunkSurvivesSignalSplitting() {
        List<ActivityClusterHeuristics.Cluster> clusters = ActivityClusterHeuristics.find(List.of(
            observation(4, -3, 78, 76, 5, mask(
                EvidenceFamily.GROWTH,
                EvidenceFamily.HARVEST,
                EvidenceFamily.FARM_GEOMETRY,
                EvidenceFamily.LIGHTING
            ), 32, 12)
        ), 8);

        assertEquals(1, clusters.size());
        assertEquals(1, clusters.getFirst().members());
        assertEquals(4, clusters.getFirst().families());
    }

    @Test
    void repeatedSpatialGrowthCanCorroborateAcrossChunksByItself() {
        List<ActivityClusterHeuristics.Cluster> clusters = ActivityClusterHeuristics.find(List.of(
            observation(0, 0, 30, 55, 6, mask(EvidenceFamily.GROWTH), 14, 0),
            observation(1, 0, 30, 55, 6, mask(EvidenceFamily.GROWTH), 14, 0)
        ), 8);

        assertEquals(1, clusters.size());
        assertEquals(2, clusters.getFirst().members());
    }

    @Test
    void outputAndComponentWorkStayHardBounded() {
        ArrayList<ActivityClusterHeuristics.Observation> observations = new ArrayList<>();
        for (int cluster = 0; cluster < 20; cluster++) {
            int start = cluster * 5;
            observations.add(observation(start, 0, 52, 58, 3, mask(EvidenceFamily.GROWTH, EvidenceFamily.FARM_GEOMETRY), 18, 4));
            observations.add(observation(start + 1, 0, 50, 56, 3, mask(EvidenceFamily.GROWTH, EvidenceFamily.HARVEST), 17, 4));
        }
        assertEquals(4, ActivityClusterHeuristics.find(observations, 4).size());
    }

    @Test
    void maximumRememberedTraceSetStaysSubsecondAndRejectsLongTrails() {
        ArrayList<ActivityClusterHeuristics.Observation> observations = new ArrayList<>(12_000);
        for (int index = 0; index < 12_000; index++) {
            observations.add(observation(
                index, 0, 45, 52, 2,
                mask(EvidenceFamily.GROWTH, EvidenceFamily.FARM_GEOMETRY),
                15, 4
            ));
        }
        assertTimeout(Duration.ofSeconds(1), () ->
            assertTrue(ActivityClusterHeuristics.find(observations, 24).isEmpty())
        );
    }

    private static ActivityClusterHeuristics.Observation observation(
        int x,
        int z,
        int score,
        int confidence,
        int observations,
        long families,
        int growth,
        int support
    ) {
        return new ActivityClusterHeuristics.Observation(
            x, z, score, confidence, 90, 90, observations, families, growth, support
        );
    }

    private static long mask(EvidenceFamily... families) {
        return ActivityClusterHeuristics.mask(families);
    }
}
