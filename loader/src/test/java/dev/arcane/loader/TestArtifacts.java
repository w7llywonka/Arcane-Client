package dev.arcane.loader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class TestArtifacts {
    private TestArtifacts() {
    }

    static void writeOuterLoader(Path destination, String version) throws Exception {
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (JarOutputStream nested = new JarOutputStream(nestedBytes)) {
            write(nested, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"arcaneclient\","
                + "\"version\":\"2.9.1+mc26.3\"}");
        }
        try (JarOutputStream outer = new JarOutputStream(Files.newOutputStream(destination))) {
            write(outer, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"arcaneloader\","
                + "\"version\":\"" + version + "\",\"jars\":[{\"file\":"
                + "\"META-INF/jars/arcane-client.jar\"}]}");
            outer.putNextEntry(new JarEntry("META-INF/jars/arcane-client.jar"));
            outer.write(nestedBytes.toByteArray());
            outer.closeEntry();
        }
    }

    static void writeRootMod(Path destination, String id, String version) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(destination))) {
            write(jar, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"" + id
                + "\",\"version\":\"" + version + "\"}");
        }
    }

    static ReleaseManifest release(Path artifact, String version, KeyPair pair) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(Files.readAllBytes(artifact));
        return new ReleaseManifest(2, "arcane-loader", version, "26.3",
            "https://example.com/Arcane-Loader.jar", ArtifactVerifier.sha256(artifact),
            Base64.getEncoder().encodeToString(signer.sign()), Files.size(artifact));
    }

    private static void write(JarOutputStream output, String name, String value) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
