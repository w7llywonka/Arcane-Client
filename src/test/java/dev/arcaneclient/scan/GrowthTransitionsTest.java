package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.model.SignalCategory;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GrowthTransitionsTest {
    @Test
    void detectsEveryAmethystStageInsteadOfOnlyRemoval() {
        GrowthTransitions.GrowthEvent event = GrowthTransitions.analyze(
            "small_amethyst_bud", Map.of(),
            "medium_amethyst_bud", Map.of()
        );

        assertEquals(GrowthTransitions.Family.AMETHYST, event.family());
        assertEquals(SignalCategory.NATURAL_GROWTH, event.category());
        assertTrue(event.strength() >= 30);
    }

    @Test
    void treatsBerryResetAsHarvestNotPassiveGrowth() {
        GrowthTransitions.GrowthEvent event = GrowthTransitions.analyze(
            "sweet_berry_bush", Map.of("age", 3),
            "sweet_berry_bush", Map.of("age", 1)
        );

        assertEquals(GrowthTransitions.Family.BERRY, event.family());
        assertEquals(SignalCategory.LIVE_ACTIVITY, event.category());
        assertTrue(event.strength() >= 150);
    }

    @Test
    void decodesKelpAdditionAndHarvestAcrossBlockIds() {
        GrowthTransitions.GrowthEvent growth = GrowthTransitions.analyze("water", Map.of(), "kelp", Map.of("age", 2));
        GrowthTransitions.GrowthEvent harvest = GrowthTransitions.analyze("kelp_plant", Map.of(), "water", Map.of());

        assertEquals(GrowthTransitions.Family.KELP, growth.family());
        assertEquals(SignalCategory.NATURAL_GROWTH, growth.category());
        assertEquals(GrowthTransitions.Family.KELP, harvest.family());
        assertEquals(SignalCategory.LIVE_ACTIVITY, harvest.category());
    }

    @Test
    void ignoresUnrelatedStateChanges() {
        assertNull(GrowthTransitions.analyze("stone", Map.of(), "deepslate", Map.of()));
    }
}
