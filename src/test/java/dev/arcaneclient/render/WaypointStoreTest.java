package dev.arcaneclient.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaypointStoreTest {
    @Test
    void namesAreStableBoundedAndHumanReadable() {
        assertEquals("Waypoint", WaypointStore.cleanName("   "));
        assertEquals("Nether roof portal", WaypointStore.cleanName("  Nether   roof  portal  "));
        assertEquals(32, WaypointStore.cleanName("abcdefghijklmnopqrstuvwxyz0123456789").length());
    }

    @Test
    void singleplayerIdentityIncludesLevelAndSaveFolder() {
        assertEquals(
            "singleplayer:survival world@world-01",
            WaypointStore.singleplayerIdentity(" Survival World ", Path.of("saves", "World-01"))
        );
    }
}
