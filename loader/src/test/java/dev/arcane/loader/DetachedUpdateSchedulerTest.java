package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DetachedUpdateSchedulerTest {
    @TempDir
    Path directory;

    @Test
    void helperCommandTargetsTheSelectedOuterJar() {
        UpdatePlan plan = new UpdatePlan(true,
            directory.resolve("instance/mods/Arcane-Loader.jar"),
            directory.resolve("state/updates/new.jar"),
            directory.resolve("state/backups/old.jar"), "1.2.2", "1.2.3");
        ReleaseManifest release = new ReleaseManifest(2, "arcane-loader", "1.2.3",
            "26.3", "https://example.com/loader.jar", "a".repeat(64),
            "A".repeat(86) + "==", 1234);
        List<String> command = DetachedUpdateScheduler.command(Path.of("java"),
            directory.resolve("state/helper.jar"), 42, plan, release,
            directory.resolve("state/helper.log"));

        assertEquals("java", command.get(0));
        assertEquals("-jar", command.get(1));
        assertTrue(command.contains(plan.runningLoader().toString()));
        assertTrue(command.contains(plan.stagedLoader().toString()));
        assertTrue(command.contains(plan.backupLoader().toString()));
        assertEquals("42", command.get(3));
    }
}
