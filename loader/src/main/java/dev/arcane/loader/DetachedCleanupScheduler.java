package dev.arcane.loader;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class DetachedCleanupScheduler {
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private final Path stateDirectory;

    DetachedCleanupScheduler(Path stateDirectory) {
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
    }

    void schedule(List<Path> redundantJars) throws Exception {
        if (redundantJars.isEmpty() || !SCHEDULED.compareAndSet(false, true)) return;
        Path helper = DetachedUpdateScheduler.writeHelperJar(stateDirectory);
        Path backup = stateDirectory.resolve("backups").resolve("legacy-"
            + Instant.now().toString().replace(':', '-'));
        Path log = stateDirectory.resolve("update-helper.log");
        List<String> command = command(DetachedUpdateScheduler.resolveJavaLauncher(), helper,
            ProcessHandle.current().pid(), backup, redundantJars);
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> DetachedUpdateScheduler.launch(command, log),
            "Arcane legacy cleanup launcher"));
    }

    static List<String> command(Path javaLauncher, Path helper, long parentPid, Path backup,
                                List<Path> redundantJars) {
        List<String> command = new ArrayList<>();
        command.add(javaLauncher.toString());
        command.add("-jar");
        command.add(helper.toString());
        command.add("--cleanup");
        command.add(Long.toString(parentPid));
        command.add(backup.toString());
        redundantJars.forEach(path -> command.add(path.toString()));
        return List.copyOf(command);
    }
}
