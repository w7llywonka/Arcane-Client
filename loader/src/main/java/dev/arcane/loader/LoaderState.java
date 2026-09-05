package dev.arcane.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;

final class LoaderState {
    private final Path directory;
    private final Path file;
    private final Properties values = new Properties();

    private LoaderState(Path directory) {
        this.directory = directory;
        this.file = directory.resolve("loader.properties");
    }

    static LoaderState load() throws IOException {
        String appData = System.getenv("APPDATA");
        Path directory = appData == null || appData.isBlank()
            ? Path.of(System.getProperty("user.home"), ".arcane-loader")
            : Path.of(appData, "Arcane Loader");
        return at(directory);
    }

    static LoaderState at(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        LoaderState state = new LoaderState(normalized);
        if (Files.exists(state.file)) {
            try (InputStream input = Files.newInputStream(state.file)) {
                state.values.load(input);
            }
        }
        return state;
    }

    Path directory() {
        return directory;
    }

    String get(String key, String fallback) {
        return values.getProperty(key, fallback);
    }

    void put(String key, String value) {
        if (value == null || value.isBlank()) values.remove(key);
        else values.setProperty(key, value);
    }

    void save() throws IOException {
        Path temporary = Files.createTempFile(directory, "loader", ".properties.tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, "Arcane Loader state. Do not share this file.");
        }
        try {
            Files.setPosixFilePermissions(temporary, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows inherits the current user's AppData ACL.
        }
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
