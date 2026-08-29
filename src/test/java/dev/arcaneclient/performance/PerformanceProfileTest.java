package dev.arcaneclient.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PerformanceProfileTest {
    @Test
    void highFpsIsTheDefaultProfile() {
        assertEquals(PerformanceProfile.HIGH_FPS, PerformanceProfile.fromConfig(0));
        assertEquals("HIGH FPS", PerformanceProfile.HIGH_FPS.label());
        assertFalse(PerformanceProfile.HIGH_FPS.filledStorageBoxes());
    }

    @Test
    void budgetsScaleDeliberatelyAcrossProfiles() {
        assertEquals(1_000_000L, PerformanceProfile.HIGH_FPS.scanBudgetNanos(3));
        assertEquals(1_850_000L, PerformanceProfile.BALANCED.scanBudgetNanos(3));
        assertEquals(2_500_000L, PerformanceProfile.QUALITY.scanBudgetNanos(3));

        for (int intensity = 1; intensity <= 16; intensity++) {
            long highFps = PerformanceProfile.HIGH_FPS.scanBudgetNanos(intensity);
            long balanced = PerformanceProfile.BALANCED.scanBudgetNanos(intensity);
            long quality = PerformanceProfile.QUALITY.scanBudgetNanos(intensity);
            assertTrue(highFps < balanced);
            assertTrue(balanced < quality);
        }

        assertTrue(PerformanceProfile.HIGH_FPS.storageTargetLimit() < PerformanceProfile.BALANCED.storageTargetLimit());
        assertTrue(PerformanceProfile.BALANCED.storageTargetLimit() < PerformanceProfile.QUALITY.storageTargetLimit());
        assertTrue(PerformanceProfile.HIGH_FPS.itemTargetLimit() < PerformanceProfile.BALANCED.itemTargetLimit());
        assertTrue(PerformanceProfile.HIGH_FPS.tunnelTargetLimit() < PerformanceProfile.QUALITY.tunnelTargetLimit());
        assertTrue(PerformanceProfile.HIGH_FPS.snapshotRefreshTicks() > PerformanceProfile.QUALITY.snapshotRefreshTicks());
    }

    @Test
    void profileSelectionClampsAndCycles() {
        assertEquals(PerformanceProfile.HIGH_FPS, PerformanceProfile.fromConfig(-10));
        assertEquals(PerformanceProfile.QUALITY, PerformanceProfile.fromConfig(99));
        assertEquals(PerformanceProfile.BALANCED, PerformanceProfile.HIGH_FPS.next());
        assertEquals(PerformanceProfile.QUALITY, PerformanceProfile.BALANCED.next());
        assertEquals(PerformanceProfile.HIGH_FPS, PerformanceProfile.QUALITY.next());
    }
}
