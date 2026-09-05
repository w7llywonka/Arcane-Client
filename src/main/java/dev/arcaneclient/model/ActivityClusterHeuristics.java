package dev.arcaneclient.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded spatial clustering for growth and activity evidence.
 *
 * <p>The cluster pass intentionally has no storage input. It promotes either a
 * dense, repeatedly observed chunk or a compact connected component whose
 * members corroborate one another with growth/activity families. Two-chunk
 * gap bridging is allowed only when both chunks share a primary family, which
 * catches deliberately split farms without turning unrelated weak markers into
 * one long cluster.</p>
 */
public final class ActivityClusterHeuristics {
    private static final int MAX_COMPONENT_SIZE = 64;
    private static final long PRIMARY_FAMILIES = mask(
        EvidenceFamily.GROWTH,
        EvidenceFamily.FARM_GEOMETRY,
        EvidenceFamily.HARVEST,
        EvidenceFamily.AUTOMATION,
        EvidenceFamily.MANAGED_HABITAT
    );

    private ActivityClusterHeuristics() {
    }

    public static List<Cluster> find(Collection<Observation> observations, int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must not be negative");
        if (limit == 0 || observations.isEmpty()) return List.of();

        Map<Long, Observation> indexed = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (!eligible(observation)) continue;
            indexed.merge(observation.key(), observation, (first, second) -> quality(second) > quality(first) ? second : first);
        }
        if (indexed.isEmpty()) return List.of();

        ArrayList<Observation> seeds = new ArrayList<>(indexed.values());
        seeds.sort(Comparator.comparingInt(ActivityClusterHeuristics::quality).reversed()
            .thenComparingInt(Observation::chunkX)
            .thenComparingInt(Observation::chunkZ));
        Set<Long> visited = new HashSet<>();
        ArrayList<Cluster> clusters = new ArrayList<>();

        for (Observation seed : seeds) {
            if (!visited.add(seed.key())) continue;
            ArrayList<Observation> component = new ArrayList<>();
            ArrayDeque<Observation> frontier = new ArrayDeque<>();
            frontier.add(seed);
            while (!frontier.isEmpty() && component.size() < MAX_COMPONENT_SIZE) {
                Observation current = frontier.removeFirst();
                component.add(current);
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        Observation neighbor = indexed.get(key(current.chunkX() + dx, current.chunkZ() + dz));
                        if (neighbor == null || visited.contains(neighbor.key()) || !linked(current, neighbor)) continue;
                        if (component.size() + frontier.size() >= MAX_COMPONENT_SIZE) continue;
                        if (visited.add(neighbor.key())) frontier.addLast(neighbor);
                    }
                }
            }
            Cluster cluster = summarize(component);
            if (cluster != null) clusters.add(cluster);
        }

        clusters.sort(Comparator.comparingInt(Cluster::confidence).reversed()
            .thenComparing(Comparator.comparingInt(Cluster::members).reversed())
            .thenComparingInt(Cluster::chunkX)
            .thenComparingInt(Cluster::chunkZ));
        return List.copyOf(clusters.subList(0, Math.min(limit, clusters.size())));
    }

    public static long mask(EvidenceFamily... families) {
        long mask = 0L;
        for (EvidenceFamily family : families) mask |= 1L << family.ordinal();
        return mask;
    }

    private static boolean eligible(Observation observation) {
        return observation.score() >= 22
            && observation.confidence() >= 30
            && observation.freshness() >= 20
            && observation.coverage() >= 25
            && observation.growthStrength() >= 6
            && (observation.familyMask() & PRIMARY_FAMILIES) != 0L;
    }

    private static boolean linked(Observation first, Observation second) {
        int distance = Math.max(Math.abs(first.chunkX() - second.chunkX()), Math.abs(first.chunkZ() - second.chunkZ()));
        long firstPrimary = first.familyMask() & PRIMARY_FAMILIES;
        long secondPrimary = second.familyMask() & PRIMARY_FAMILIES;
        long sharedPrimary = firstPrimary & secondPrimary;
        if (distance <= 1) {
            return sharedPrimary != 0L || Long.bitCount(firstPrimary | secondPrimary) >= 2;
        }
        return distance == 2
            && sharedPrimary != 0L
            && first.growthStrength() >= 10
            && second.growthStrength() >= 10
            && first.confidence() >= 40
            && second.confidence() >= 40;
    }

    private static Cluster summarize(List<Observation> members) {
        if (members.isEmpty()) return null;
        int totalScore = 0;
        int totalConfidence = 0;
        int totalFreshness = 0;
        int totalCoverage = 0;
        int totalGrowth = 0;
        int totalSupport = 0;
        int repeated = 0;
        long families = 0L;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Observation representative = members.getFirst();

        for (Observation member : members) {
            totalScore += member.score();
            totalConfidence += member.confidence();
            totalFreshness += member.freshness();
            totalCoverage += member.coverage();
            totalGrowth += member.growthStrength();
            totalSupport += member.supportStrength();
            repeated += Math.max(0, member.observations() - 1);
            families |= member.familyMask();
            minX = Math.min(minX, member.chunkX());
            maxX = Math.max(maxX, member.chunkX());
            minZ = Math.min(minZ, member.chunkZ());
            maxZ = Math.max(maxZ, member.chunkZ());
            if (quality(member) > quality(representative)) representative = member;
        }

        int count = members.size();
        int familyCount = Long.bitCount(families);
        int averageScore = totalScore / count;
        int averageConfidence = totalConfidence / count;
        int averageFreshness = totalFreshness / count;
        int averageCoverage = totalCoverage / count;
        boolean denseSingle = count == 1
            && averageScore >= 72
            && averageConfidence >= 68
            && averageFreshness >= 40
            && averageCoverage >= 45
            && familyCount >= 3
            && totalGrowth >= 16
            && members.getFirst().observations() >= 3;
        boolean repeatedSingleFamilyGrowth = familyCount == 1
            && totalGrowth >= 24
            && repeated >= 6;
        boolean corroborated = count >= 2
            && totalGrowth >= 18
            && (familyCount >= 2 || repeatedSingleFamilyGrowth)
            && averageConfidence >= 35
            && averageFreshness >= 30
            && (repeated > 0 || familyCount >= 3 || totalScore >= 90);
        if (!denseSingle && !corroborated) return null;

        int span = Math.max(maxX - minX, maxZ - minZ);
        int confidence = averageScore * 30 / 100
            + averageConfidence * 30 / 100
            + averageFreshness * 15 / 100
            + averageCoverage * 10 / 100
            + Math.min(12, familyCount * 3)
            + Math.min(10, count * 3)
            + Math.min(8, (totalGrowth + totalSupport) / 20)
            - Math.max(0, span - 1) * 3;
        confidence = Math.clamp(confidence, 0, 100);
        if (confidence < 50) return null;

        ArrayList<Long> memberChunks = new ArrayList<>(count);
        for (Observation member : members) memberChunks.add(member.key());
        memberChunks.sort(Long::compare);
        return new Cluster(
            representative.chunkX(), representative.chunkZ(), confidence, count,
            familyCount, totalGrowth, List.copyOf(memberChunks)
        );
    }

    private static int quality(Observation observation) {
        return observation.score() * 2 + observation.confidence() + observation.growthStrength() + observation.supportStrength() / 2;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long)chunkX & 0xFFFFFFFFL) | ((long)chunkZ << 32);
    }

    public record Observation(
        int chunkX,
        int chunkZ,
        int score,
        int confidence,
        int freshness,
        int coverage,
        int observations,
        long familyMask,
        int growthStrength,
        int supportStrength
    ) {
        public Observation {
            if (score < 0 || score > 100 || confidence < 0 || confidence > 100
                || freshness < 0 || freshness > 100 || coverage < 0 || coverage > 100) {
                throw new IllegalArgumentException("percentage values must be between 0 and 100");
            }
            if (observations < 0 || growthStrength < 0 || supportStrength < 0) {
                throw new IllegalArgumentException("evidence values must not be negative");
            }
        }

        long key() {
            return ActivityClusterHeuristics.key(this.chunkX, this.chunkZ);
        }
    }

    public record Cluster(
        int chunkX,
        int chunkZ,
        int confidence,
        int members,
        int families,
        int growthStrength,
        List<Long> memberChunks
    ) {
        public Cluster {
            memberChunks = List.copyOf(memberChunks);
        }
    }
}
