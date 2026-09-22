package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LegacyModCleanupTest {
    @TempDir
    Path directory;

    @Test
    void plansAndMovesOnlyUnselectedArcaneRootMods() throws Exception {
        Path mods = Files.createDirectories(directory.resolve("mods"));
        Path selectedLoader = mods.resolve("ARCLoader.jar");
        Path selectedExternalClient = mods.resolve("selected-client.jar");
        Path oldLoader = mods.resolve("Arcane-Loader-1.2.1.jar");
        Path oldClient = mods.resolve("arcane-client-2.9.1.jar");
        Path unrelated = mods.resolve("fabric-api.jar");
        TestArtifacts.writeOuterLoader(selectedLoader, "1.2.2+mc26.3");
        TestArtifacts.writeRootMod(selectedExternalClient, "arcaneclient", "2.9.1");
        TestArtifacts.writeOuterLoader(oldLoader, "1.2.1+mc26.3");
        TestArtifacts.writeRootMod(oldClient, "arcaneclient", "2.9.1");
        TestArtifacts.writeRootMod(unrelated, "fabric-api", "1.0.0");

        List<Path> redundant = LegacyModCleanup.findRedundant(mods,
            Set.of(selectedLoader, selectedExternalClient));

        assertEquals(Set.of(oldLoader.toAbsolutePath(), oldClient.toAbsolutePath()),
            Set.copyOf(redundant));
        Path backup = directory.resolve("state/backups/legacy");
        DetachedUpdater.cleanupAfterExit(-1, backup, redundant);
        assertFalse(Files.exists(oldLoader));
        assertFalse(Files.exists(oldClient));
        assertTrue(Files.exists(backup.resolve(oldLoader.getFileName())));
        assertTrue(Files.exists(backup.resolve(oldClient.getFileName())));
        assertTrue(Files.exists(selectedLoader));
        assertTrue(Files.exists(selectedExternalClient));
        assertTrue(Files.exists(unrelated));
    }
}
