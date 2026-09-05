package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StreamerPrivacyTest {
    @Test
    void streamerModeRedactsServersCoordinatesAndPlayerNames() {
        assertFalse(StreamerPrivacy.mayRevealSensitive(true));
        assertTrue(StreamerPrivacy.mayRevealSensitive(false));
        assertEquals("HIDDEN", StreamerPrivacy.sensitiveValue(true, "server.example"));
        assertEquals("12, -8", StreamerPrivacy.sensitiveValue(false, "12, -8"));
        assertEquals("PLAYER", StreamerPrivacy.entityName(true, true, "RealName"));
        assertEquals("Zombie", StreamerPrivacy.entityName(true, false, "Zombie"));
    }
}
