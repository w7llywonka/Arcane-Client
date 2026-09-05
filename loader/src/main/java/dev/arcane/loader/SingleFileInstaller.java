package dev.arcane.loader;

import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Optional desktop entrypoint support: copies this outer loader, never its nested client. */
final class SingleFileInstaller {
    private static final String DISTRIBUTION_NAME = "ARCLoader.jar";

    private SingleFileInstaller() {
    }

    static InstallResult installCurrent(Path modsDirectory) throws Exception {
        Path source = currentDistribution();
        Path mods = modsDirectory.toAbsolutePath().normalize();
        Path destination = mods.resolve(DISTRIBUTION_NAME);
        if (source.equals(destination)) {
            return new InstallResult(destination, ArcaneLoader.LOADER_VERSION, false);
        }
        if (Files.isRegularFile(destination)
            && Files.size(source) == Files.size(destination)
            && ArtifactVerifier.sha256(source).equals(ArtifactVerifier.sha256(destination))) {
            return new InstallResult(destination, ArcaneLoader.LOADER_VERSION, false);
        }
        Files.createDirectories(mods);
        Path temporary = Files.createTempFile(mods, ".arcane-loader-install-", ".jar.tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveReplace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new InstallResult(destination, ArcaneLoader.LOADER_VERSION, true);
    }

    private static Path currentDistribution() throws Exception {
        URI location = ArcaneLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path source = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase().endsWith(".jar")) {
            throw new IllegalStateException("Run the built ARCLoader JAR to install it");
        }
        return source.toRealPath();
    }

    private static void moveReplace(Path source, Path destination) throws Exception {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
