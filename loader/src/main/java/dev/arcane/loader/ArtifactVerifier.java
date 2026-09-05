package dev.arcane.loader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

final class ArtifactVerifier {
    private ArtifactVerifier() {
    }

    static void verify(Path jar, String expectedSha256, String signatureBase64, PublicKey publicKey) throws Exception {
        String actual = sha256(jar);
        if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            expectedSha256.toLowerCase().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new SecurityException("Downloaded mod hash does not match the signed release manifest");
        }
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        try (InputStream input = Files.newInputStream(jar)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) verifier.update(buffer, 0, read);
        }
        if (!verifier.verify(Base64.getDecoder().decode(signatureBase64))) {
            throw new SecurityException("Downloaded mod signature is invalid");
        }
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
