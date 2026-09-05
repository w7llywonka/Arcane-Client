package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CobbledDeepslateTrailHeuristicsTest {
    @Test
    void connectedDescendingStaircaseQualifies() {
        List<BlockPosition> points = List.of(
            point(0, 64, 0), point(1, 63, 0), point(2, 62, 0),
            point(3, 61, 0), point(4, 60, 0), point(5, 59, 0)
        );

        List<CobbledDeepslateTrailHeuristics.Trail> trails =
            CobbledDeepslateTrailHeuristics.detect(points, 8);

        assertEquals(1, trails.size());
        assertEquals(5, trails.getFirst().verticalSpan());
        assertEquals(point(5, 59, 0), trails.getFirst().lowest());
        assertEquals(6, trails.getFirst().points().size());
        assertTrue(trails.getFirst().confidence() >= 75);
    }

    @Test
    void flatFloorAndDisconnectedColumnsAreRejected() {
        List<BlockPosition> floor = List.of(
            point(0, -30, 0), point(1, -30, 0), point(2, -30, 0),
            point(3, -30, 0), point(4, -30, 0), point(5, -30, 0)
        );
        List<BlockPosition> gaps = List.of(
            point(0, 60, 0), point(0, 58, 0), point(0, 56, 0),
            point(0, 54, 0), point(0, 52, 0)
        );

        assertTrue(CobbledDeepslateTrailHeuristics.detect(floor, 8).isEmpty());
        assertTrue(CobbledDeepslateTrailHeuristics.detect(gaps, 8).isEmpty());
    }

    @Test
    void inputAndOutputWorkStayBounded() {
        ArrayList<BlockPosition> points = new ArrayList<>();
        for (int component = 0; component < 100; component++) {
            for (int step = 0; step < 8; step++) {
                points.add(point(component * 20 + step, 100 - step, component * 20));
            }
        }

        assertTimeout(Duration.ofMillis(250), () ->
            assertEquals(3, CobbledDeepslateTrailHeuristics.detect(points, 3).size())
        );
    }

    private static BlockPosition point(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }
}
