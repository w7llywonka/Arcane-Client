package dev.arcaneclient.additions.nuker;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class NukerConfigTest {
    @Test void defaultsRequireDeliberateInputAndProtectFloorAndContainers() {
        var c = new NukerConfig();
        assertFalse(c.enabled); assertTrue(c.holdAttack);
        assertFalse(c.accepts("minecraft:stone", 63, 64, false));
        assertFalse(c.accepts("minecraft:chest", 64, 64, true));
        assertTrue(c.accepts("minecraft:stone", 64, 64, false));
    }
    @Test void explicitSelectionsAndEmptySelectionAreRespected() {
        var c = new NukerConfig(); c.allBlocks = false;
        assertFalse(c.accepts("minecraft:stone", 64, 64, false));
        c.blocks.add("minecraft:stone");
        assertTrue(c.accepts("minecraft:stone", 64, 64, false));
        assertFalse(c.accepts("minecraft:dirt", 64, 64, false));
        c.protectFloor = c.protectBlockEntities = false;
        c.blocks.add("minecraft:chest");
        assertTrue(c.accepts("minecraft:chest", 63, 64, true));
    }
    @Test void importedValuesAreBoundedAndSelectionsStayMutable() {
        var c = new NukerConfig(); c.range = 999; c.delayTicks = 0;
        c.blocks = new ArrayList<>(Arrays.asList(null, "bad id", "minecraft:stone", "minecraft:stone"));
        c.sanitize();
        assertEquals(6, c.range); assertEquals(1, c.delayTicks);
        assertEquals(1, c.blocks.size()); c.blocks.clear();
        c.blocks = null; c.sanitize(); assertNotNull(c.blocks);
    }
    @Test void settingsRoundTripInSharedConfigs() {
        var c = new NukerConfig(); c.range = 3; c.delayTicks = 7; c.allBlocks = false;
        c.blocks.add("minecraft:andesite");
        var copy = new Gson().fromJson(new Gson().toJson(c), NukerConfig.class);
        copy.sanitize(); assertEquals(c.blocks, copy.blocks);
        assertEquals(7, copy.delayTicks); assertEquals(3, copy.range); assertFalse(copy.allBlocks);
    }
}
