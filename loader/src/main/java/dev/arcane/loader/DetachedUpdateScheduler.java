package dev.arcane.loader;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Launches a tiny updater outside Minecraft after the game JVM begins shutting down. */
final class DetachedUpdateScheduler implements UpdateScheduler {
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private final Path stateDirectory;

    DetachedUpdateScheduler(Path stateDirectory) {
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
    }

    @Override
    public void schedule(UpdatePlan plan, ReleaseManifest release) throws Exception {
        if (!SCHEDULED.compareAndSet(false, true)) return;
        Path helper = writeHelperJar(stateDirectory);
        Path log = stateDirectory.resolve("update-helper.log");
        long parentPid = ProcessHandle.current().pid();
        List<String> command = command(resolveJavaLauncher(), helper, parentPid, plan, release, log);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> launch(command, log),
            "Arcane loader replacement launcher"));
    }

    static List<String> command(Path javaLauncher, Path helper, long parentPid, UpdatePlan plan,
                                ReleaseManifest release, Path log) {
        List<String> command = new ArrayList<>();
        command.add(javaLauncher.toString());
        command.add("-jar");
        command.add(helper.toString());
        command.add(Long.toString(parentPid));
        command.add(plan.stagedLoader().toString());
        command.add(plan.runningLoader().toString());
        command.add(plan.backupLoader().toString());
        command.add(Long.toString(release.size()));
        command.add(release.sha256());
        command.add(release.signature());
        command.add(log.toString());
        return List.copyOf(command);
    }

    static void launch(List<String> command, Path log) {
        try {
            Files.createDirectories(log.getParent());
            new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();
        } catch (Exception error) {
            System.err.println("[ARCLoader] Could not start the detached updater: "
                + error.getMessage());
        }
    }

    static Path writeHelperJar(Path requestedStateDirectory) throws Exception {
        Path stateDirectory = requestedStateDirectory.toAbsolutePath().normalize();
        Files.createDirectories(stateDirectory);
        Path destination = stateDirectory.resolve("arcane-update-helper.jar");
        Path temporary = Files.createTempFile(stateDirectory, "arcane-helper-", ".jar.tmp");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, DetachedUpdater.class.getName());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary), manifest)) {
            writeClass(output, DetachedUpdater.class);
            writeClass(output, ArtifactVerifier.class);
            writeClass(output, PemKeys.class);
            writeResource(output, "release-public.pem");
        }
        moveReplace(temporary, destination);
        return destination;
    }

    private static void writeClass(JarOutputStream output, Class<?> type) throws Exception {
        String entry = type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream("/" + entry)) {
            if (input == null) throw new IllegalStateException("Missing helper class " + entry);
            output.putNextEntry(new JarEntry(entry));
            input.transferTo(output);
            output.closeEntry();
        }
    }

    private static void writeResource(JarOutputStream output, String resource) throws Exception {
        try (InputStream input = DetachedUpdateScheduler.class.getResourceAsStream("/" + resource)) {
            if (input == null) throw new IllegalStateException("Missing helper resource " + resource);
            output.putNextEntry(new JarEntry(resource));
            input.transferTo(output);
            output.closeEntry();
        }
    }

    static Path resolveJavaLauncher() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path quiet = bin.resolve(windows ? "javaw.exe" : "java");
        if (Files.isRegularFile(quiet)) return quiet;
        Path fallback = bin.resolve(windows ? "java.exe" : "java");
        return Files.isRegularFile(fallback) ? fallback : Path.of("java");
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
