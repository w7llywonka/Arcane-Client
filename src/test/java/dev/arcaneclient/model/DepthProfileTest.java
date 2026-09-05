package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DepthProfileTest {
    @Test
    void matchesArchivedVerticalWeighting() {
        assertEquals(130, DepthProfile.adjust(100, 32));
        assertEquals(100, DepthProfile.adjust(100, 48));
        assertEquals(55, DepthProfile.adjust(100, 55));
        assertEquals(20, DepthProfile.adjust(100, 56));
    }
}
