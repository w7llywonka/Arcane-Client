package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OuterLoaderUpdaterTest {
    @TempDir
    Path directory;

    @Test
    void currentVersionPerformsNoDownloadScheduleOrModsWrite() throws Exception {
        Path mods = Files.createDirectories(directory.resolve("instance/mods"));
        Path running = mods.resolve("Arcane-Loader.jar");
        byte[] original = "current-loader".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(running, original);
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger schedules = new AtomicInteger();
        OuterLoaderUpdater updater = new OuterLoaderUpdater(
            LoaderState.at(directory.resolve("state")), pair.getPublic(),
            (source, destination, maximum) -> downloads.incrementAndGet(),
            (plan, release) -> schedules.incrementAndGet());
        ReleaseManifest same = new ReleaseManifest(2, "arcane-loader",
            "1.2.2+mc26.3", "26.3", "https://example.com/loader.jar",
            "a".repeat(64), "A".repeat(86) + "==", 999);

        LoaderUpdateResult result = updater.checkAndStage(same,
            "1.2.2+mc26.3", running);

        assertEquals(LoaderUpdateResult.Status.CURRENT, result.status());
        assertEquals(0, downloads.get());
        assertEquals(0, schedules.get());
        assertArrayEquals(original, Files.readAllBytes(running));
        try (var files = Files.list(mods)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void futureOuterLoaderStagesOutsideModsAndLeavesLiveJarUntouched() throws Exception {
        Path mods = Files.createDirectories(directory.resolve("instance/mods"));
        Path running = mods.resolve("Arcane-Loader.jar");
        byte[] original = "old-loader".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(running, original);
        Path releaseFile = directory.resolve("remote-loader.jar");
        TestArtifacts.writeOuterLoader(releaseFile, "1.2.3+mc26.3");
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ReleaseManifest release = TestArtifacts.release(releaseFile, "1.2.3+mc26.3", pair);
        AtomicReference<UpdatePlan> scheduled = new AtomicReference<>();
        OuterLoaderUpdater updater = new OuterLoaderUpdater(
            LoaderState.at(directory.resolve("state")), pair.getPublic(),
            (source, destination, maximum) -> Files.copy(releaseFile, destination,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING),
            (plan, ignored) -> scheduled.set(plan));

        LoaderUpdateResult result = updater.checkAndStage(release,
            "1.2.2+mc26.3", running);

        assertEquals(LoaderUpdateResult.Status.STAGED, result.status());
        assertTrue(Files.isRegularFile(result.path()));
        assertFalse(result.path().startsWith(mods));
        assertArrayEquals(original, Files.readAllBytes(running));
        assertEquals(result.path(), scheduled.get().stagedLoader());
        try (var files = Files.list(mods)) {
            assertEquals(1, files.count());
        }
    }
}
