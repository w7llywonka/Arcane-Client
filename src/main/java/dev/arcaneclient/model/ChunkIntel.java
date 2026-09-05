package dev.arcaneclient.model;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Bounded scanner metadata shared by Chunk Intel and the optional evidence-point overlay. */
@Environment(EnvType.CLIENT)
public record ChunkIntel(
    int confidence,
    int observations,
    int completeSamples,
    int coveragePercent,
    int freshness,
    int independentFamilies,
    List<EvidencePoint> evidencePoints
) {
    public static final ChunkIntel EMPTY = new ChunkIntel(0, 0, 0, 0, 0, 0, List.of());

    public ChunkIntel {
        confidence = clampPercent(confidence);
        coveragePercent = clampPercent(coveragePercent);
        freshness = clampPercent(freshness);
        if (observations < 0 || completeSamples < 0 || independentFamilies < 0) {
            throw new IllegalArgumentException("chunk intelligence counts must not be negative");
        }
        evidencePoints = List.copyOf(evidencePoints);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
