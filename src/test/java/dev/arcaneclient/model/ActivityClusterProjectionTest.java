package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ActivityClusterProjectionTest {
    @Test
    void undergroundPaletteAndBlockEntityEvidenceContributeNothing() {
        ActivityClusterProjection.Projection projection = ActivityClusterProjection.project(List.of(
            point(SignalCategory.PLACED_BLOCK, EvidenceFamily.EXCAVATION, -50, "worked deepslate", 200, 8),
            point(SignalCategory.BLOCK_ENTITY, EvidenceFamily.AUTOMATION, -40, "functional block entity", 200, 8),
            point(SignalCategory.LIGHT_LEAK, EvidenceFamily.LIGHTING, -30, "concealed light", 200, 8)
        ), 100, 100, 6);

        assertEquals(0, projection.score());
        assertEquals(0, projection.confidence());
        assertEquals(0L, projection.familyMask());
        assertEquals(0, projection.observations());
    }

    @Test
    void trustedGrowthProjectionIsIndependentOfHeight() {
        List<EvidencePoint> deep = List.of(
            point(SignalCategory.NATURAL_GROWTH, EvidenceFamily.GROWTH, -55, "repeated growth chronicle", 90, 3),
            point(SignalCategory.CULTIVATION, EvidenceFamily.FARM_GEOMETRY, -54, "aligned crop row", 90, 3)
        );
        List<EvidencePoint> surface = List.of(
            point(SignalCategory.NATURAL_GROWTH, EvidenceFamily.GROWTH, 100, "repeated growth chronicle", 90, 3),
            point(SignalCategory.CULTIVATION, EvidenceFamily.FARM_GEOMETRY, 101, "aligned crop row", 90, 3)
        );

        ActivityClusterProjection.Projection deepProjection = ActivityClusterProjection.project(deep, 90, 85, 3);
        ActivityClusterProjection.Projection surfaceProjection = ActivityClusterProjection.project(surface, 90, 85, 3);

        assertEquals(surfaceProjection, deepProjection);
        assertTrue(deepProjection.score() >= 22);
        assertTrue(deepProjection.confidence() >= 30);
        assertTrue(deepProjection.growthStrength() >= 18);
    }

    @Test
    void oneNoisyFamilyCannotQualifyByRepeatingItself() {
        ActivityClusterProjection.Projection projection = ActivityClusterProjection.project(List.of(
            point(SignalCategory.CULTIVATION, EvidenceFamily.FARM_GEOMETRY, 64, "crop row", 2000, 128)
        ), 100, 100, 6);

        assertTrue(projection.score() < 22);
        assertEquals(1, Long.bitCount(projection.familyMask()));
    }

    @Test
    void distributedRepeatedGrowthCanQualifyWithoutUndergroundPaletteHelp() {
        List<EvidencePoint> points = java.util.stream.IntStream.range(0, 6)
            .mapToObj(index -> new EvidencePoint(
                SignalCategory.NATURAL_GROWTH,
                EvidenceFamily.GROWTH,
                new BlockPosition(index, -40, index % 2),
                "masked growth state revealed",
                40,
                10L,
                1,
                true
            ))
            .toList();

        ActivityClusterProjection.Projection projection = ActivityClusterProjection.project(points, 90, 85, 1);

        assertTrue(projection.score() >= 22);
        assertTrue(projection.confidence() >= 30);
        assertEquals(1, Long.bitCount(projection.familyMask()));
    }

    @Test
    void displayOnlyAccessTrailCannotCreateOrBoostAHit() {
        ActivityClusterProjection.Projection trailOnly = ActivityClusterProjection.project(List.of(
            point(SignalCategory.PLACED_BLOCK, EvidenceFamily.ACCESS_TRAIL, -50,
                "descending cobbled-deepslate access trail", 200, 12)
        ), 100, 100, 6);
        ActivityClusterProjection.Projection growthOnly = ActivityClusterProjection.project(List.of(
            point(SignalCategory.NATURAL_GROWTH, EvidenceFamily.GROWTH, 90,
                "repeated growth chronicle", 100, 4)
        ), 100, 100, 6);
        ActivityClusterProjection.Projection combined = ActivityClusterProjection.project(List.of(
            point(SignalCategory.PLACED_BLOCK, EvidenceFamily.ACCESS_TRAIL, -50,
                "descending cobbled-deepslate access trail", 200, 12),
            point(SignalCategory.NATURAL_GROWTH, EvidenceFamily.GROWTH, 90,
                "repeated growth chronicle", 100, 4)
        ), 100, 100, 6);

        assertEquals(ActivityClusterProjection.Projection.EMPTY, trailOnly);
        assertEquals(growthOnly, combined);
    }

    @Test
    void amethystActivityAndStaticGeodeShellAreDisplayOnly() {
        ActivityClusterProjection.Projection projection = ActivityClusterProjection.project(List.of(
            point(SignalCategory.NATURAL_GROWTH, EvidenceFamily.AMETHYST_ACTIVITY, -40,
                "amethyst stage advanced while loaded", 100, 3),
            point(SignalCategory.NATURAL_GROWTH, EvidenceFamily.EXCAVATION, -40,
                "stripped geode shell", 200, 20)
        ), 90, 90, 3);

        assertEquals(ActivityClusterProjection.Projection.EMPTY, projection);
    }

    private static EvidencePoint point(
        SignalCategory category,
        EvidenceFamily family,
        int y,
        String reason,
        int strength,
        int observations
    ) {
        return new EvidencePoint(category, family, new BlockPosition(1, y, 1), reason, strength, 10L, observations, false);
    }
}
