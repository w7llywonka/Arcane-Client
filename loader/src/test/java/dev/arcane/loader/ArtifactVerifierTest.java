package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArtifactVerifierTest {
    @TempDir
    java.nio.file.Path directory;

    @Test
    void acceptsOnlyMatchingHashAndSignature() throws Exception {
        var file = directory.resolve("arcane.jar");
        Files.writeString(file, "verified-arcane-artifact");
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(Files.readAllBytes(file));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String hash = ArtifactVerifier.sha256(file);
        assertDoesNotThrow(() -> ArtifactVerifier.verify(file, hash, signature, pair.getPublic()));
        Files.writeString(file, "tampered");
        assertThrows(SecurityException.class, () -> ArtifactVerifier.verify(file, hash, signature, pair.getPublic()));
    }
}
