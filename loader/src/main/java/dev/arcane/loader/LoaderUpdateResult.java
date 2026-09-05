package dev.arcane.loader;

import java.nio.file.Path;

record LoaderUpdateResult(Status status, String version, Path path) {
    enum Status {
        CURRENT,
        STAGED
    }
}
