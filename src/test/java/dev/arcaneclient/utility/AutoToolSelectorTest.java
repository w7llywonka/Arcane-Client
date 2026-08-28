package dev.arcaneclient.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class AutoToolSelectorTest {
    @Test
    void suitabilityWinsThenMiningSpeedBreaksTheTie() {
        float[] speeds = {1, 20, 4, 8, 1, 1, 1, 1, 1};
        boolean[] suitable = {false, false, true, true, false, false, false, false, false};
        boolean[] eligible = eligible();

        assertEquals(3, AutoToolSelector.choose(0, speeds, suitable, eligible));
    }

    @Test
    void keepsCurrentSlotOnEqualScoreAndSkipsIneligibleTool() {
        float[] speeds = {1, 8, 8, 1, 1, 1, 1, 1, 1};
        boolean[] suitable = {false, true, true, false, false, false, false, false, false};
        boolean[] eligible = eligible();
        eligible[1] = false;

        assertEquals(2, AutoToolSelector.choose(2, speeds, suitable, eligible));
    }

    private static boolean[] eligible() {
        boolean[] result = new boolean[9];
        Arrays.fill(result, true);
        return result;
    }
}
