package dev.arcane.loader;

import java.nio.file.Path;

record InstallResult(Path path, String version, boolean changed) {
}
