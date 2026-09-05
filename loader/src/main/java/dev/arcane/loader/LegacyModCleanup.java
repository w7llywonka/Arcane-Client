package dev.arcane.loader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Plans a shutdown-only cleanup of redundant standalone Arcane JARs from older releases. */
final class LegacyModCleanup {
    private static final int MAX_METADATA_BYTES = 128 * 1024;

    private LegacyModCleanup() {
    }

    static List<Path> findRedundant(Path modsDirectory, Set<Path> selectedOrigins) throws Exception {
        Path mods = modsDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(mods)) return List.of();
        Set<Path> selected = new HashSet<>();
        for (Path origin : selectedOrigins) selected.add(origin.toAbsolutePath().normalize());
        List<Path> redundant = new ArrayList<>();
        try (var files = Files.list(mods)) {
            for (Path path : files.toList()) {
                Path candidate = path.toAbsolutePath().normalize();
                if (selected.contains(candidate) || Files.isSymbolicLink(candidate)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || !candidate.getFileName().toString().toLowerCase().endsWith(".jar")) {
                    continue;
                }
                String id = rootFabricModId(candidate);
                if ("arcaneclient".equals(id) || "arcaneloader".equals(id)) redundant.add(candidate);
            }
        }
        return List.copyOf(redundant);
    }

    private static String rootFabricModId(Path jarPath) {
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (var input = jar.getInputStream(entry)) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_METADATA_BYTES) return null;
                    output.write(buffer, 0, read);
                }
            }
            Object id = Json.parseObject(output.toString(StandardCharsets.UTF_8)).get("id");
            return id instanceof String value ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
