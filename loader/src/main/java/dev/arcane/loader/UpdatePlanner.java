package dev.arcane.loader;

import java.nio.file.Path;

final class UpdatePlanner {
    private UpdatePlanner() {
    }

    static UpdatePlan plan(Path stateDirectory, Path runningLoader, String currentVersion,
                           ReleaseManifest release) {
        Path target = runningLoader.toAbsolutePath().normalize();
        Path state = stateDirectory.toAbsolutePath().normalize();
        boolean newer = VersionComparator.compare(release.version(), currentVersion) > 0;
        if (!newer) {
            return new UpdatePlan(false, target, null, null, currentVersion, release.version());
        }
        String safeVersion = release.version().replaceAll("[^A-Za-z0-9.+_-]", "_");
        Path staged = state.resolve("updates").resolve("arcane-loader-" + safeVersion + ".jar");
        Path backup = state.resolve("backups").resolve(
            "arcane-loader-" + currentVersion.replaceAll("[^A-Za-z0-9.+_-]", "_") + ".jar");
        return new UpdatePlan(true, target, staged, backup, currentVersion, release.version());
    }
}
