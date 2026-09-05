package dev.arcaneclient.esp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BlockEntityEspClassifierTest {
    @Test
    void chatAlertsOnlyUseInventoryLikeBlockEntities() {
        assertTrue(BlockEntityEspClassifier.isContainerTarget("chest"));
        assertTrue(BlockEntityEspClassifier.isContainerTarget("barrel"));
        assertTrue(BlockEntityEspClassifier.isContainerTarget("hopper"));
        assertTrue(BlockEntityEspClassifier.isContainerTarget("crafter"));
        assertFalse(BlockEntityEspClassifier.isContainerTarget("mob_spawner"));
        assertFalse(BlockEntityEspClassifier.isContainerTarget("beacon"));
    }

    @Test
    void storageEspDoesNotAbsorbSearchAndPortalTargets() {
        assertTrue(BlockEntityEspClassifier.isStorageTarget("ender_chest"));
        assertTrue(BlockEntityEspClassifier.isStorageTarget("shulker_box"));
        assertFalse(BlockEntityEspClassifier.isStorageTarget("mob_spawner"));
        assertFalse(BlockEntityEspClassifier.isStorageTarget("vault"));
        assertFalse(BlockEntityEspClassifier.isStorageTarget("beacon"));
        assertFalse(BlockEntityEspClassifier.isStorageTarget("end_gateway"));
    }

    @Test
    void debugOnlyAcceptsBlockEntitiesBelowTheDeepslateCutoff() {
        assertEquals(BlockEntityEspClassifier.DEBUG_COLOR, BlockEntityEspClassifier.debugColor(-64));
        assertEquals(BlockEntityEspClassifier.DEBUG_COLOR, BlockEntityEspClassifier.debugColor(-1));
        assertEquals(0, BlockEntityEspClassifier.debugColor(0));
        assertEquals(0, BlockEntityEspClassifier.debugColor(1));
        assertEquals(0, BlockEntityEspClassifier.debugColor(80));
    }
}
