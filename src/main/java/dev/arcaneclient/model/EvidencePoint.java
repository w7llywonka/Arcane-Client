package dev.arcaneclient.model;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Immutable, render-safe observation retained for the bounded Evidence Points overlay. */
@Environment(EnvType.CLIENT)
public record EvidencePoint(
    SignalCategory category,
    EvidenceFamily family,
    BlockPosition position,
    String reason,
    int strength,
    long lastSeenTick,
    int observations,
    boolean live
) {
    public EvidencePoint {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(position, "position");
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("reason must not be blank");
        if (strength <= 0) throw new IllegalArgumentException("strength must be positive");
        if (observations <= 0) throw new IllegalArgumentException("observations must be positive");
    }
}
