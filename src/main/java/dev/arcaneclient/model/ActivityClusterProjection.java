package dev.arcaneclient.model;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Projects raw chunk evidence onto the small set of signals trusted by the
 * activity-cluster finder.
 *
 * <p>This projection is intentionally independent of block Y and excludes
 * excavation, palette, lighting, placed-block, and block-entity evidence.
 * Those inputs are unreliable when a server obfuscates underground chunk
 * palettes. Repeated evidence is capped per family before it can affect the
 * cluster score.</p>
 */
@Environment(EnvType.CLIENT)
public final class ActivityClusterProjection {
    private ActivityClusterProjection() {
    }

    public static Projection project(
        Collection<EvidencePoint> points,
        int freshness,
        int coverage,
        int completeSamples
    ) {
        EnumMap<EvidenceFamily, Integer> rawByFamily = new EnumMap<>(EvidenceFamily.class);
        Set<BlockPosition> positions = new HashSet<>();
        int observations = 0;
        long familyMask = 0L;

        for (EvidencePoint point : points) {
            if (!trusted(point)) continue;
            rawByFamily.merge(point.family(), point.strength(), ActivityClusterProjection::saturatingAdd);
            positions.add(point.position());
            observations = Math.min(128, observations + point.observations());
            familyMask |= 1L << point.family().ordinal();
        }

        int growthStrength = contribution(rawByFamily, EvidenceFamily.GROWTH, 14)
            + contribution(rawByFamily, EvidenceFamily.FARM_GEOMETRY, 18)
            + contribution(rawByFamily, EvidenceFamily.HARVEST, 22);
        int supportStrength = contribution(rawByFamily, EvidenceFamily.AUTOMATION, 18)
            + contribution(rawByFamily, EvidenceFamily.MANAGED_HABITAT, 16);
        int families = Long.bitCount(familyMask);
        if (families == 0) return Projection.EMPTY;
        int repeated = Math.max(0, observations - families);
        int repeatBonus = families >= 2
            ? Math.min(8, repeated)
            : positions.size() >= 4 && observations >= 6
                ? Math.min(12, 4 + positions.size())
                : 0;
        int score = Math.min(100,
            growthStrength
                + supportStrength
                + Math.min(18, Math.max(0, families - 1) * 6)
                + repeatBonus
        );
        int boundedFreshness = Math.clamp(freshness, 0, 100);
        int boundedCoverage = Math.clamp(coverage, 0, 100);
        int confidence = Math.clamp(
            score * 45 / 100
                + boundedFreshness * 20 / 100
                + boundedCoverage * 15 / 100
                + Math.min(10, families * 2)
                + Math.min(10, Math.max(0, completeSamples) * 2 + Math.min(4, repeated)),
            0,
            100
        );
        return new Projection(score, confidence, observations, familyMask, growthStrength, supportStrength);
    }

    private static boolean trusted(EvidencePoint point) {
        if (point.category() == SignalCategory.BLOCK_ENTITY) return false;
        return switch (point.family()) {
            case GROWTH, FARM_GEOMETRY, HARVEST, AUTOMATION, MANAGED_HABITAT -> true;
            default -> false;
        };
    }

    private static int contribution(Map<EvidenceFamily, Integer> rawByFamily, EvidenceFamily family, int cap) {
        int raw = rawByFamily.getOrDefault(family, 0);
        if (raw <= 0) return 0;
        return (int)Math.min(cap, (long)cap * raw / (raw + 20L));
    }

    private static int saturatingAdd(int first, int second) {
        return (int)Math.min(Integer.MAX_VALUE, (long)first + second);
    }

    public record Projection(
        int score,
        int confidence,
        int observations,
        long familyMask,
        int growthStrength,
        int supportStrength
    ) {
        public static final Projection EMPTY = new Projection(0, 0, 0, 0L, 0, 0);
    }
}
