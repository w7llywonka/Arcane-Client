package dev.arcaneclient.model;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Exact, bounded world locations used by specialized base-finding ESP layers. */
@Environment(EnvType.CLIENT)
public record WorldObservation(BlockPosition position, Kind kind, int confidence, boolean live, int stage) {
    public WorldObservation(BlockPosition position, Kind kind, int confidence, boolean live) {
        this(position, kind, confidence, live, -1);
    }

    public WorldObservation {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(kind, "kind");
        confidence = Math.clamp(confidence, 0, 100);
        stage = Math.clamp(stage, -1, 3);
    }

    public enum Kind {
        AMETHYST_SHARD,
        COBBLED_DEEPSLATE_TRAIL
    }
}
