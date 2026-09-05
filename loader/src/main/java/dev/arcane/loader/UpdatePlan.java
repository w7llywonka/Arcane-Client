package dev.arcane.loader;

import java.nio.file.Path;

record UpdatePlan(
    boolean updateAvailable,
    Path runningLoader,
    Path stagedLoader,
    Path backupLoader,
    String currentVersion,
    String releaseVersion
) {
}
