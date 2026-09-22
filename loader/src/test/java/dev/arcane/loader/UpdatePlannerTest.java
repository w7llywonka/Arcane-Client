package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UpdatePlannerTest {
    @TempDir
    Path directory;

    @Test
    void sameOrOlderReleaseProducesNoPathsToWrite() {
        Path live = directory.resolve("mods/arcane-loader.jar");
        UpdatePlan same = UpdatePlanner.plan(directory.resolve("state"), live,
            "1.2.2+mc26.3", release("1.2.2+mc26.3"));
        assertFalse(same.updateAvailable());
        assertNull(same.stagedLoader());
        assertNull(same.backupLoader());

        UpdatePlan older = UpdatePlanner.plan(directory.resolve("state"), live,
            "1.2.2+mc26.3", release("1.2.1+mc26.3"));
        assertFalse(older.updateAvailable());
    }

    @Test
    void newerReleaseStagesOnlyOutsideMods() {
        Path state = directory.resolve("state");
        Path mods = directory.resolve("instance/mods");
        UpdatePlan plan = UpdatePlanner.plan(state, mods.resolve("Arcane-Loader.jar"),
            "1.2.2+mc26.3", release("1.2.3+mc26.3"));
        assertTrue(plan.updateAvailable());
        assertTrue(plan.stagedLoader().startsWith(state.toAbsolutePath()));
        assertFalse(plan.stagedLoader().startsWith(mods.toAbsolutePath()));
        assertTrue(plan.backupLoader().startsWith(state.toAbsolutePath()));
    }

    private static ReleaseManifest release(String version) {
        return new ReleaseManifest(2, "arcane-loader", version, "26.3",
            "https://example.com/loader.jar", "a".repeat(64),
            "A".repeat(86) + "==", 1);
    }
}
