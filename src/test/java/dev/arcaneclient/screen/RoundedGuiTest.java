package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RoundedGuiTest {
    @Test
    void cornerInsetsShrinkTowardTheCenter() {
        int previous = Integer.MAX_VALUE;
        for (int row = 0; row < 7; row++) {
            int inset = RoundedGui.cornerInset(7, row);
            assertTrue(inset >= 0);
            assertTrue(inset <= previous);
            previous = inset;
        }
        assertEquals(1, previous);
    }

    @Test
    void smallRadiusStillProducesAVisibleCorner() {
        assertTrue(RoundedGui.cornerInset(4, 0) > 0);
        assertEquals(1, RoundedGui.cornerInset(4, 3));
    }
}
