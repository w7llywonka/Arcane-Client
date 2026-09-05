package dev.arcaneclient.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FullbrightPolicyTest {
    @Test
    void enabledFullbrightMakesOnlyNightVisionVisibleToTheLightmap() {
        assertTrue(FullbrightPolicy.lightmapSeesEffect(true, true, false));
        assertFalse(FullbrightPolicy.lightmapSeesEffect(true, false, false));
    }

    @Test
    void realEffectsStillPassThroughAndDisabledFullbrightDoesNothing() {
        assertTrue(FullbrightPolicy.lightmapSeesEffect(false, true, true));
        assertFalse(FullbrightPolicy.lightmapSeesEffect(false, true, false));
    }
}
