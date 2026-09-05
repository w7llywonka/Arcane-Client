package dev.arcane.loader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Rejects a signed artifact if it is not the expected outer loader with a nested client. */
final class LoaderArtifactValidator {
    private static final int MAX_METADATA_BYTES = 128 * 1024;

    private LoaderArtifactValidator() {
    }

    static void validate(Path artifact, ReleaseManifest release) throws Exception {
        try (ZipFile jar = new ZipFile(artifact.toFile())) {
            ZipEntry rootMetadata = jar.getEntry("fabric.mod.json");
            if (rootMetadata == null) throw new SecurityException("Release is not a Fabric loader mod");
            Map<String, Object> metadata = Json.parseObject(readLimited(jar.getInputStream(rootMetadata)));
            if (!"arcaneloader".equals(metadata.get("id"))) {
                throw new SecurityException("Release has the wrong Fabric mod id");
            }
            if (!release.version().equals(metadata.get("version"))) {
                throw new SecurityException("Release version does not match its Fabric metadata");
            }

            Object jarsValue = metadata.get("jars");
            if (!(jarsValue instanceof List<?> jars) || jars.size() != 1) {
                throw new SecurityException("Loader must contain exactly one nested Arcane Client");
            }
            Object nestedValue = jars.getFirst();
            if (!(nestedValue instanceof Map<?, ?> nestedObject)
                || !(nestedObject.get("file") instanceof String nestedPath)
                || !nestedPath.startsWith("META-INF/jars/")
                || !nestedPath.endsWith(".jar")) {
                throw new SecurityException("Loader has invalid nested-client metadata");
            }
            ZipEntry nestedEntry = jar.getEntry(nestedPath);
            if (nestedEntry == null) throw new SecurityException("Loader is missing its nested Arcane Client");
            validateNestedClient(jar.getInputStream(nestedEntry));
        }
    }

    private static void validateNestedClient(InputStream input) throws Exception {
        try (ZipInputStream nested = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = nested.getNextEntry()) != null) {
                if (!"fabric.mod.json".equals(entry.getName())) continue;
                Map<String, Object> metadata = Json.parseObject(readLimited(nested));
                if (!"arcaneclient".equals(metadata.get("id"))) {
                    throw new SecurityException("Nested JAR is not Arcane Client");
                }
                return;
            }
        }
        throw new SecurityException("Nested Arcane Client has no Fabric metadata");
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_METADATA_BYTES) throw new SecurityException("Fabric metadata is too large");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
