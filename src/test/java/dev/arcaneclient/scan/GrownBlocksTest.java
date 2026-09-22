package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GrownBlocksTest {
    @Test void onlyMatureStagesCount() {
        assertFalse(GrownBlocks.matches("wheat", 6, false, ""));
        assertTrue(GrownBlocks.matches("wheat", 7, false, ""));
        assertFalse(GrownBlocks.matches("sweet_berry_bush", 1, false, ""));
        assertTrue(GrownBlocks.matches("sweet_berry_bush", 2, false, ""));
        assertFalse(GrownBlocks.matches("small_amethyst_bud", -1, false, ""));
        assertFalse(GrownBlocks.matches("large_amethyst_bud", -1, false, ""));
        assertTrue(GrownBlocks.matches("amethyst_cluster", -1, false, ""));
        assertFalse(GrownBlocks.matches("cave_vines", 20, false, ""));
        assertTrue(GrownBlocks.matches("cave_vines", 20, true, ""));
    }
    @Test void stemAgeIsNotMistakenForMaturity() {
        assertFalse(GrownBlocks.matches("kelp", 25, false, ""));
        for (String id : new String[]{"kelp_plant", "vine", "bamboo", "sugar_cane", "cactus",
            "weeping_vines_plant", "twisting_vines_plant"}) assertTrue(GrownBlocks.matches(id, -1, false, ""), id);
        assertFalse(GrownBlocks.matches("pitcher_crop", 4, false, "upper"));
        assertTrue(GrownBlocks.matches("pitcher_crop", 4, false, "lower"));
    }
    @Test void containersSaplingsAndArbitraryAgesNeverCount() {
        for (String id : new String[]{"chest", "ender_chest", "spawner", "bee_nest", "beehive", "barrel",
            "furnace", "oak_sapling", "bamboo_sapling", "mangrove_propagule", "fire", "frosted_ice",
            "stone", "deepslate", "redstone_block", "oak_leaves"}) assertFalse(GrownBlocks.matches(id, 99, true, ""), id);
    }
}
