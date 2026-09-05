package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.model.SignalCategory;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GrowthTransitionsTest {
    @Test
    void mapsEveryVisibleShardBlockToItsExactGrowthStage() {
        assertEquals(0, GrowthTransitions.amethystRank("small_amethyst_bud"));
        assertEquals(1, GrowthTransitions.amethystRank("medium_amethyst_bud"));
        assertEquals(2, GrowthTransitions.amethystRank("large_amethyst_bud"));
        assertEquals(3, GrowthTransitions.amethystRank("amethyst_cluster"));
        assertEquals(-1, GrowthTransitions.amethystRank("amethyst_block"));
    }

    @Test
    void amethystStagesRemainDisplayMetadataOnly() {
        assertNull(GrowthTransitions.analyze(
            "small_amethyst_bud", Map.of(),
            "medium_amethyst_bud", Map.of()
        ));
        assertNull(GrowthTransitions.analyze(
            "air", Map.of(), "small_amethyst_bud", Map.of()
        ));
        assertNull(GrowthTransitions.analyze(
            "amethyst_cluster", Map.of(), "air", Map.of()
        ));
        assertNull(GrowthTransitions.analyze(
            "deepslate", Map.of(), "amethyst_cluster", Map.of()
        ));
        assertNull(GrowthTransitions.analyze(
            "stone", Map.of(), "large_amethyst_bud", Map.of()
        ));
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

    @Test
    void recognizesGrowthRevealedFromAnObfuscatedDeepPalette() {
        GrowthTransitions.GrowthEvent crop = GrowthTransitions.analyze(
            "deepslate", Map.of("axis", "y"),
            "wheat", Map.of("age", 4)
        );
        GrowthTransitions.GrowthEvent berry = GrowthTransitions.analyze(
            "tuff", Map.of(),
            "sweet_berry_bush", Map.of("age", 2)
        );

        assertEquals(GrowthTransitions.Family.CROP, crop.family());
        assertEquals(GrowthTransitions.Family.BERRY, berry.family());
        assertEquals(SignalCategory.NATURAL_GROWTH, crop.category());
        assertTrue(crop.strength() >= 30);
        assertNull(GrowthTransitions.analyze("deepslate", Map.of(), "redstone_wire", Map.of()));
    }

    @Test
    void rejectsStorageBeforeInterpretingGrowthProperties() {
        assertNull(GrowthTransitions.analyze(
            "beehive", Map.of("honey_level", 5),
            "beehive", Map.of("honey_level", 0)
        ));
        assertNull(GrowthTransitions.analyze(
            "chest", Map.of("age", 5),
            "chest", Map.of("age", 0)
        ));
    }
}
