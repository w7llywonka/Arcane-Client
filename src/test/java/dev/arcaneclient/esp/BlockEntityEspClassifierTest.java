package dev.arcaneclient.esp;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
