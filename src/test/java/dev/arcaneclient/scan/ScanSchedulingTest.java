package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ScanSchedulingTest {
    @Test
    void neighborhoodEnumerationIsQuadraticInsteadOfRepeatedSquares() {
        assertEquals(625, ScanScheduling.neighborhoodCoordinateCount(12));
        assertEquals(2_925, legacyCoordinateChecks(12));
        assertEquals(2_401, ScanScheduling.neighborhoodCoordinateCount(24));
        assertEquals(20_825, legacyCoordinateChecks(24));
    }

    @Test
    void frontierAndFlightDirectionDrivePriority() {
        long eastFrontier = ScanScheduling.priorityScore(true, 12, 0, 0, 0, 1, 0);
        long westFrontier = ScanScheduling.priorityScore(true, -12, 0, 0, 0, 1, 0);
        long centerRescan = ScanScheduling.priorityScore(false, 0, 0, 0, 0, 1, 0);
        assertTrue(eastFrontier < westFrontier, "forward frontier should beat equally distant chunks behind the player");
        assertTrue(westFrontier < centerRescan, "unscanned frontier should beat background rescans");
    }

    @Test
    void completionQuantumCutsDefaultFirstResultLatency() {
        int legacySlices = slicesUntilFirstCompletion(16, 1, 8);
        int prioritizedSlices = slicesUntilFirstCompletion(
            ScanScheduling.activeWindow(8),
            ScanScheduling.SLICES_PER_JOB_TURN,
            8
        );
        assertEquals(113, legacySlices);
        assertEquals(36, prioritizedSlices);
        assertTrue(prioritizedSlices * 3 <= legacySlices);
    }

    @Test
    void directionUsesADeadbandAndActiveWindowIsBounded() {
        assertEquals(0, ScanScheduling.direction(0.01));
        assertEquals(1, ScanScheduling.direction(0.2));
        assertEquals(-1, ScanScheduling.direction(-0.2));
        assertEquals(2, ScanScheduling.activeWindow(1));
        assertEquals(8, ScanScheduling.activeWindow(16));
    }

    private static int legacyCoordinateChecks(int radius) {
        int checks = 0;
        for (int ring = 0; ring <= radius; ++ring) {
            int diameter = ring * 2 + 1;
            checks += diameter * diameter;
        }
        return checks;
    }

    private static int slicesUntilFirstCompletion(int activeJobs, int slicesPerTurn, int slicesPerJob) {
        int[] remaining = new int[activeJobs];
        Arrays.fill(remaining, slicesPerJob);
        int consumed = 0;
        int current = 0;
        while (true) {
            for (int slice = 0; slice < slicesPerTurn; ++slice) {
                --remaining[current];
                ++consumed;
                if (remaining[current] == 0) {
                    return consumed;
                }
            }
            current = (current + 1) % activeJobs;
        }
    }
}
