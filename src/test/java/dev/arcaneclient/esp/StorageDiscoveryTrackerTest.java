package dev.arcaneclient.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StorageDiscoveryTrackerTest {
    @Test
    void onlyReportsEachPositionOncePerWorld() {
        StorageDiscoveryTracker tracker = new StorageDiscoveryTracker(8);

        assertTrue(tracker.markNew(100L));
        assertFalse(tracker.markNew(100L));
        tracker.reset();
        assertTrue(tracker.markNew(100L));
    }

    @Test
    void boundsRememberedPositions() {
        StorageDiscoveryTracker tracker = new StorageDiscoveryTracker(2);
        tracker.markNew(1L);
        tracker.markNew(2L);
        tracker.markNew(3L);

        assertEquals(2, tracker.size());
        assertTrue(tracker.markNew(1L));
    }
}
