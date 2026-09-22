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
    void amethystMustAdvanceAnObservedStageWithoutBeingRevealed() {
        assertEquals(GrowthTransitions.Family.AMETHYST, GrowthTransitions.analyze(
            "small_amethyst_bud", Map.of(),
            "medium_amethyst_bud", Map.of()
        ).family());
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
    void obfuscationRevealsAreNotGrowth() {
        GrowthTransitions.GrowthEvent crop = GrowthTransitions.analyze(
            "deepslate", Map.of("axis", "y"),
            "wheat", Map.of("age", 4)
        );
        GrowthTransitions.GrowthEvent berry = GrowthTransitions.analyze(
            "tuff", Map.of(),
            "sweet_berry_bush", Map.of("age", 2)
        );

        assertNull(crop);
        assertNull(berry);
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

    @Test
    void excludesEverySaplingAndUnrelatedAgeProperty() {
        for (String id : java.util.List.of("oak_sapling", "spruce_sapling", "bamboo_sapling",
            "mangrove_propagule", "bee_nest", "chest", "ender_chest", "spawner", "fire", "frosted_ice")) {
            assertNull(GrowthTransitions.analyze(id, Map.of("age", 0), id, Map.of("age", 1)), id);
        }
    }

    @Test
    void detectsBerriesVinesCropsAndKelpWithoutCountingUnchangedPlants() {
        assertEquals(GrowthTransitions.Family.BERRY, GrowthTransitions.analyze(
            "sweet_berry_bush", Map.of("age", 1), "sweet_berry_bush", Map.of("age", 2)).family());
        assertEquals(GrowthTransitions.Family.CAVE_VINE, GrowthTransitions.analyze(
            "cave_vines", Map.of("berries", false), "cave_vines", Map.of("berries", true)).family());
        assertEquals(GrowthTransitions.Family.CAVE_VINE, GrowthTransitions.analyze(
            "vine", Map.of("north", true, "east", false), "vine", Map.of("north", true, "east", true)).family());
        assertEquals(GrowthTransitions.Family.KELP, GrowthTransitions.analyze(
            "kelp", Map.of("age", 10), "kelp_plant", Map.of()).family());
        assertEquals(GrowthTransitions.Family.CROP, GrowthTransitions.analyze(
            "wheat", Map.of("age", 2), "wheat", Map.of("age", 3)).family());
        assertNull(GrowthTransitions.analyze("vine", Map.of("north", true), "vine", Map.of("north", true)));
        assertNull(GrowthTransitions.analyze("small_amethyst_bud", Map.of("facing", "up"),
            "medium_amethyst_bud", Map.of("facing", "down")));
    }
}
