package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReleaseManifestTest {
    private static final String VALID = """
        {
          "schemaVersion": 2,
          "artifactType": "arcane-loader",
          "version": "1.2.2+mc1.21.11",
          "minecraftVersion": "1.21.11",
          "downloadUrl": "https://example.com/arcane.jar",
          "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "signature": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
          "size": 789851
        }
        """;

    @Test
    void parsesSupportedManifest() {
        ReleaseManifest release = ReleaseManifest.parse(VALID);
        assertEquals("1.2.2+mc1.21.11", release.version());
        assertEquals("arcane-loader", release.artifactType());
        assertEquals(789851L, release.size());
    }

    @Test
    void rejectsUnsupportedSchema() {
        assertThrows(IllegalArgumentException.class,
            () -> ReleaseManifest.parse(VALID.replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")));
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class,
            () -> ReleaseManifest.parse(VALID.replace("\"size\": 789851", "\"size\": 0")));
    }

    @Test
    void rejectsLegacyClientArtifactManifest() {
        assertThrows(IllegalArgumentException.class,
            () -> ReleaseManifest.parse(VALID.replace("arcane-loader", "arcane-client")));
    }
}
