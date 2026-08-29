package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiAnimationTest {
    @Test
    void approachClosesTheGapWithoutOvershooting() {
        float value = 0.0f;
        for (int frame = 0; frame < 200; frame++) {
            float next = UiAnimation.approach(value, 1.0f, 1.0f / 60.0f, UiAnimation.SWITCH_SPEED);
            assertTrue(next >= value, "an animation toward one never moves backward");
            assertTrue(next <= 1.0f, "an animation never overshoots its target");
            value = next;
        }
        assertEquals(1.0f, value);
    }

    @Test
    void aStalledFrameCannotSnapAnAnimationPastItsCap() {
        float slow = UiAnimation.approach(0.0f, 1.0f, UiAnimation.MAX_STEP_SECONDS, UiAnimation.SWITCH_SPEED);
        float stalled = UiAnimation.approach(0.0f, 1.0f, 30.0f, UiAnimation.SWITCH_SPEED);
        assertEquals(slow, stalled);
        assertEquals(0.0f, UiAnimation.approach(0.0f, 1.0f, 0.0f, UiAnimation.SWITCH_SPEED));
    }

    @Test
    void easingIsClampedAndSymmetricAroundTheMidpoint() {
        assertEquals(0.0f, UiAnimation.ease(-2.0f));
        assertEquals(1.0f, UiAnimation.ease(2.0f));
        assertEquals(0.5f, UiAnimation.ease(0.5f));
        assertEquals(1.0f, UiAnimation.ease(0.25f) + UiAnimation.ease(0.75f), 1.0e-6f);
    }

    @Test
    void theFirstFrameAdoptsItsTargetSoOpeningTheGuiPlaysNoAnimation() {
        UiAnimation.Track track = new UiAnimation.Track();
        assertEquals(1.0f, track.advance(true, 1.0f / 60.0f, UiAnimation.SWITCH_SPEED));

        float value = track.advance(false, 1.0f / 60.0f, UiAnimation.SWITCH_SPEED);
        assertTrue(value < 1.0f && value > 0.0f, "a later change animates instead of snapping");

        for (int frame = 0; frame < 200; frame++) {
            value = track.advance(false, 1.0f / 60.0f, UiAnimation.SWITCH_SPEED);
        }
        assertEquals(0.0f, value);
    }
}
