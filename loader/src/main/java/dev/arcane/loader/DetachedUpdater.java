package dev.arcane.loader;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Minimal JDK-only helper extracted outside mods so Windows can unlock and replace the loader. */
public final class DetachedUpdater {
    private static final Duration EXIT_TIMEOUT = Duration.ofMinutes(2);

    private DetachedUpdater() {
    }

    public static void main(String[] args) {
        if (args.length >= 4 && "--cleanup".equals(args[0])) {
            try {
                long parentPid = Long.parseLong(args[1]);
                Path backup = Path.of(args[2]);
                List<Path> candidates = new ArrayList<>();
                for (int index = 3; index < args.length; index++) candidates.add(Path.of(args[index]));
                cleanupAfterExit(parentPid, backup, candidates);
                System.out.println("Arcane legacy cleanup completed");
            } catch (Exception error) {
                System.err.println("Arcane legacy cleanup failed: " + error.getMessage());
                error.printStackTrace(System.err);
                System.exit(1);
            }
            return;
        }
        if (args.length != 8) {
            System.err.println("Arcane detached updater received invalid arguments");
            System.exit(2);
        }
        try {
            long parentPid = Long.parseLong(args[0]);
            Path staged = Path.of(args[1]);
            Path target = Path.of(args[2]);
            Path backup = Path.of(args[3]);
            long size = Long.parseLong(args[4]);
            replaceAfterExit(parentPid, staged, target, backup, size, args[5], args[6]);
            System.out.println("ARCLoader update installed successfully at " + target);
        } catch (Exception error) {
            System.err.println("ARCLoader replacement failed: " + error.getMessage());
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void replaceAfterExit(long parentPid, Path staged, Path target, Path backup,
                                 long expectedSize, String sha256, String signature) throws Exception {
        Path source = staged.toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        Path savedCopy = backup.toAbsolutePath().normalize();
        validatePaths(source, destination);
        waitForExit(parentPid);
        PublicKey key = loadPublicKey();
        replaceVerified(source, destination, savedCopy, expectedSize, sha256, signature, key);
    }

    static void replaceAfterExit(long parentPid, Path staged, Path target, Path backup,
                                 long expectedSize, String sha256, String signature,
                                 PublicKey key) throws Exception {
        Path source = staged.toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        Path savedCopy = backup.toAbsolutePath().normalize();
        validatePaths(source, destination);
        waitForExit(parentPid);
        replaceVerified(source, destination, savedCopy, expectedSize, sha256, signature, key);
    }

    private static void replaceVerified(Path source, Path destination, Path savedCopy,
                                        long expectedSize, String sha256, String signature,
                                        PublicKey key) throws Exception {
        verify(source, expectedSize, sha256, signature, key);

        Files.createDirectories(savedCopy.getParent());
        if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(destination, savedCopy, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        }

        Path targetDirectory = destination.getParent();
        Files.createDirectories(targetDirectory);
        Path localTemporary = Files.createTempFile(targetDirectory,
            ".arcane-loader-update-", ".jar.tmp");
        try {
            Files.copy(source, localTemporary, StandardCopyOption.REPLACE_EXISTING);
            verify(localTemporary, expectedSize, sha256, signature, key);
            moveReplaceWithRetry(localTemporary, destination);
            verify(destination, expectedSize, sha256, signature, key);
            Files.deleteIfExists(source);
        } finally {
            Files.deleteIfExists(localTemporary);
        }
    }

    private static void validatePaths(Path source, Path destination) {
        if (source.equals(destination)) throw new SecurityException("Staged loader cannot be the live loader");
        if (!destination.getFileName().toString().toLowerCase().endsWith(".jar")) {
            throw new SecurityException("Live loader target is not a JAR");
        }
    }

    static void cleanupAfterExit(long parentPid, Path backupDirectory,
                                 List<Path> candidates) throws Exception {
        waitForExit(parentPid);
        Path backup = backupDirectory.toAbsolutePath().normalize();
        Files.createDirectories(backup);
        for (Path requested : candidates) {
            Path candidate = requested.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(candidate)
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                || !isArcaneRootMod(candidate)) {
                continue;
            }
            Path destination = uniqueDestination(backup, candidate.getFileName().toString());
            try {
                Files.move(candidate, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException ignored) {
                moveAcrossVolumes(candidate, destination);
            }
        }
    }

    private static boolean isArcaneRootMod(Path candidate) {
        Pattern idPattern = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"(arcaneclient|arcaneloader)\\\"");
        try (ZipFile jar = new ZipFile(candidate.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            if (entry == null || entry.getSize() > 128 * 1024) return false;
            try (InputStream input = jar.getInputStream(entry)) {
                byte[] bytes = input.readNBytes(128 * 1024 + 1);
                if (bytes.length > 128 * 1024) return false;
                return idPattern.matcher(new String(bytes,
                    java.nio.charset.StandardCharsets.UTF_8)).find();
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path uniqueDestination(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(suffix++ + "-" + fileName);
        }
        return candidate;
    }

    private static void moveAcrossVolumes(Path source, Path destination) throws Exception {
        try {
            Files.move(source, destination);
        } catch (java.io.IOException crossVolume) {
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
            if (Files.size(source) != Files.size(destination)) {
                Files.deleteIfExists(destination);
                throw new java.io.IOException("Legacy backup size check failed", crossVolume);
            }
            Files.delete(source);
        }
    }

    private static void waitForExit(long pid) throws Exception {
        if (pid <= 0 || pid == ProcessHandle.current().pid()) return;
        ProcessHandle parent = ProcessHandle.of(pid).orElse(null);
        if (parent == null || !parent.isAlive()) return;
        parent.onExit().get(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void verify(Path artifact, long expectedSize, String sha256, String signature,
                               PublicKey key) throws Exception {
        if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
            || Files.size(artifact) != expectedSize) {
            throw new SecurityException("Staged loader size is invalid");
        }
        ArtifactVerifier.verify(artifact, sha256, signature, key);
    }

    private static PublicKey loadPublicKey() throws Exception {
        try (InputStream input = DetachedUpdater.class.getResourceAsStream("/release-public.pem")) {
            if (input == null) throw new IllegalStateException("Missing release verification key");
            return PemKeys.readPublic(new String(input.readAllBytes(),
                java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    private static void moveReplace(Path source, Path destination) throws Exception {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveReplaceWithRetry(Path source, Path destination) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 20; attempt++) {
            try {
                moveReplace(source, destination);
                return;
            } catch (java.io.IOException failure) {
                lastFailure = failure;
                if (attempt == 20) break;
                Thread.sleep(250L);
            }
        }
        throw new java.io.IOException("Could not replace the unlocked loader after 20 attempts",
            lastFailure);
    }
}
