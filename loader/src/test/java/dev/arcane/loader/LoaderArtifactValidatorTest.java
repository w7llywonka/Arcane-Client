package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LoaderArtifactValidatorTest {
    @TempDir
    Path directory;

    @Test
    void acceptsOnlyOuterLoaderWithMatchingVersionAndNestedClient() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path loader = directory.resolve("loader.jar");
        TestArtifacts.writeOuterLoader(loader, "1.2.3+mc26.3");
        ReleaseManifest release = TestArtifacts.release(loader, "1.2.3+mc26.3", pair);
        assertDoesNotThrow(() -> LoaderArtifactValidator.validate(loader, release));

        ReleaseManifest wrongVersion = new ReleaseManifest(2, "arcane-loader",
            "1.2.7+mc26.3", "26.3", release.downloadUrl(), release.sha256(),
            release.signature(), release.size());
        assertThrows(SecurityException.class,
            () -> LoaderArtifactValidator.validate(loader, wrongVersion));

        Path plainClient = directory.resolve("client.jar");
        Files.writeString(plainClient, "not an outer loader");
        assertThrows(Exception.class,
            () -> LoaderArtifactValidator.validate(plainClient, release));
    }
}
