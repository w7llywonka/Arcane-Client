package dev.arcaneclient.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WorldIntelPolicyTest {
    @Test
    void searchClassifierKeepsPortalAndUtilityResultsDistinct() {
        assertEquals(WorldIntelPolicy.SearchKind.PORTAL, WorldIntelPolicy.classify("nether_portal"));
        assertEquals(WorldIntelPolicy.SearchKind.SEARCH, WorldIntelPolicy.classify("trial_spawner"));
        assertEquals(WorldIntelPolicy.SearchKind.SEARCH, WorldIntelPolicy.classify("ancient_debris"));
        assertEquals(WorldIntelPolicy.SearchKind.NONE, WorldIntelPolicy.classify("stone"));
    }

    @Test
    void breadcrumbSpacingPreventsPointSpam() {
        assertFalse(WorldIntelPolicy.recordBreadcrumb(6.24, false));
        assertTrue(WorldIntelPolicy.recordBreadcrumb(6.25, false));
        assertTrue(WorldIntelPolicy.recordBreadcrumb(0.0, true));
    }

    @Test
    void logoutRequiresTheLastKnownChunkToRemainLoaded() {
        assertTrue(WorldIntelPolicy.probableLogout(true, false, true, false, false));
        assertFalse(WorldIntelPolicy.probableLogout(true, false, false, false, false));
        assertFalse(WorldIntelPolicy.probableLogout(true, false, true, true, false));
        assertFalse(WorldIntelPolicy.probableLogout(true, false, true, false, true));
    }

    @Test
    void logoutSpotsExpireDeterministically() {
        long now = 1_000_000L;
        assertTrue(WorldIntelPolicy.liveSpot(now, now - 60_000L, 2));
        assertFalse(WorldIntelPolicy.liveSpot(now, now - 121_000L, 2));
    }
}
