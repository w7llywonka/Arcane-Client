package dev.arcaneclient.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicConfigFileTest {
    @TempDir Path directory;

    @Test void replacesSettingsWithACompleteUtf8Document() throws IOException {
        Path target = directory.resolve("config/arcane-client.json");
        AtomicConfigFile.write(target, "{\"overlay\":false}");
        AtomicConfigFile.write(target, "{\"overlay\":true,\"label\":\"世界\"}");
        assertEquals("{\"overlay\":true,\"label\":\"世界\"}", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(1, files.count(), "No temporary settings files should remain");
        }
    }

    @Test void failedReplacementPreservesTheExistingTargetAndCleansUp() throws IOException {
        Path target = Files.createDirectory(directory.resolve("occupied"));
        Files.writeString(target.resolve("preserve.txt"), "existing user data");
        assertThrows(IOException.class, () -> AtomicConfigFile.write(target, "{}"));
        assertEquals("existing user data", Files.readString(target.resolve("preserve.txt")));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
