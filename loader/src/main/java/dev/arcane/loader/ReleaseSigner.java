package dev.arcane.loader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Signature;
import java.util.Base64;

public final class ReleaseSigner {
    private ReleaseSigner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ReleaseSigner <private-key.pem> <mod.jar>");
            System.exit(2);
        }
        Path privateKeyPath = Path.of(args[0]);
        Path jar = Path.of(args[1]);
        var privateKey = PemKeys.readPrivate(Files.readString(privateKeyPath));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        try (InputStream input = Files.newInputStream(jar)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) signer.update(buffer, 0, read);
        }
        System.out.println("sha256=" + ArtifactVerifier.sha256(jar));
        System.out.println("signature=" + Base64.getEncoder().encodeToString(signer.sign()));
    }
}
