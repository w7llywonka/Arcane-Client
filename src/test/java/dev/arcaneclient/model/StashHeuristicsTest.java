package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class StashHeuristicsTest {
    @Test
    void directCandidateRequiresDeepMultiSignalEvidence() {
        Map<SignalCategory, Integer> categories = Map.of(
            SignalCategory.INFRASTRUCTURE, 16,
            SignalCategory.PLACED_BLOCK, 5,
            SignalCategory.INTERACTION, 5
        );

        assertTrue(StashHeuristics.direct(78, true, categories));
        assertFalse(StashHeuristics.direct(78, false, categories));
        assertFalse(StashHeuristics.direct(77, true, categories));
    }

    @Test
    void neighboringChunksMustCorroborateMachineryAndHumanActivity() {
        Map<SignalCategory, Integer> first = Map.of(
            SignalCategory.INFRASTRUCTURE, 8,
            SignalCategory.PLACED_BLOCK, 8
        );
        Map<SignalCategory, Integer> second = Map.of(
            SignalCategory.BLOCK_ENTITY, 8,
            SignalCategory.INTERACTION, 8
        );

        assertTrue(StashHeuristics.clusterPair(65, true, first, 65, true, second));
        assertFalse(StashHeuristics.clusterPair(64, true, first, 65, true, second));
    }
}
