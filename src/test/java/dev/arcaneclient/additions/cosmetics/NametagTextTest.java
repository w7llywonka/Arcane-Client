package dev.arcaneclient.additions.cosmetics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NametagTextTest {
    @Test void streamerModeRedactsNamesWhileKeepingBadgeAndSelectedData() {
        NametagConfig c = new NametagConfig();
        String label = NametagText.player(c, "PrivatePlayer", true, true, 24, 13);
        assertEquals("ARC  PLAYER  24 HP  13m", label);
        assertFalse(label.contains("PrivatePlayer"));
    }

    @Test void everyFieldCanBeDisabledWithoutDanglingSeparators() {
        NametagConfig c = new NametagConfig();
        c.name = c.health = c.distance = c.stackCount = false;
        assertEquals("", NametagText.player(c, "PrivatePlayer", false, false, 20, 13));
        assertEquals("ARC", NametagText.player(c, "PrivatePlayer", false, true, 20, 13));
        assertEquals("", NametagText.item(c, "Diamond", 64, 13));
        c.health = true;
        assertEquals("20 HP", NametagText.player(c, "PrivatePlayer", false, false, 20, 13));
    }

    @Test void itemCountAndDistanceAreIndependent() {
        NametagConfig c = new NametagConfig();
        c.name = false;
        c.distance = false;
        assertEquals("×64", NametagText.item(c, "Diamond", 64, 13));
        c.stackCount = false;
        c.itemDistance = true;
        assertEquals("13m", NametagText.item(c, "Diamond", 64, 13));
    }

    @Test void opacityAndScaleHaveSafeBounds() {
        NametagConfig c = new NametagConfig();
        c.scale = -1;
        c.backgroundOpacity = 999;
        c.sanitize();
        assertEquals(50, c.scale);
        assertEquals(0xFF000000, c.backgroundColor());
        c.backgroundOpacity = 0;
        assertEquals(0, c.backgroundColor());
    }
}
