package dev.arcaneclient.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ScannerStorageFilterTest {
    @Test
    void rejectsEveryInventoryBearingScannerPath() {
        for (String path : new String[]{
            "chest", "trapped_chest", "ender_chest", "copper_chest", "barrel",
            "white_shulker_box", "chiseled_bookshelf", "shelf", "hopper", "crafter",
            "dispenser", "dropper", "furnace", "blast_furnace", "smoker",
            "brewing_stand", "beehive", "bee_nest", "lectern", "jukebox"
        }) {
            assertTrue(ScannerStorageFilter.isStoragePath(path), path);
        }
    }

    @Test
    void keepsGrowthAndNonStorageActivityAvailable() {
        for (String path : new String[]{
            "wheat", "farmland", "sweet_berry_bush", "budding_amethyst",
            "pointed_dripstone", "redstone_wire", "copper_golem_statue", "spawner"
        }) {
            assertFalse(ScannerStorageFilter.isStoragePath(path), path);
        }
    }
}
