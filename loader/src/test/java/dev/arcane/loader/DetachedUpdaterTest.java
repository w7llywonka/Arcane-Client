package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DetachedUpdaterTest {
    @TempDir
    Path directory;

    @Test
    void atomicallyReplacesOnlyOuterLoaderAndKeepsBackup() throws Exception {
        Path state = Files.createDirectories(directory.resolve("state"));
        Path mods = Files.createDirectories(directory.resolve("instance/mods"));
        Path staged = state.resolve("new-loader.jar");
        Path target = mods.resolve("Arcane-Loader.jar");
        Path backup = state.resolve("backups/old-loader.jar");
        byte[] oldBytes = "old outer loader".getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = "new signed outer loader".getBytes(StandardCharsets.UTF_8);
        Files.write(target, oldBytes);
        Files.write(staged, newBytes);
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ReleaseManifest signed = TestArtifacts.release(staged, "1.2.3+mc26.3", pair);

        DetachedUpdater.replaceAfterExit(-1, staged, target, backup, signed.size(),
            signed.sha256(), signed.signature(), pair.getPublic());

        assertArrayEquals(newBytes, Files.readAllBytes(target));
        assertArrayEquals(oldBytes, Files.readAllBytes(backup));
        assertFalse(Files.exists(staged));
    }
}
