package dev.arcaneclient.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, bounded geometry pass for player-made cobbled-deepslate descents. */
public final class CobbledDeepslateTrailHeuristics {
    private static final int MAX_INPUT_POINTS = 2048;
    private static final int MAX_COMPONENT_POINTS = 128;

    private CobbledDeepslateTrailHeuristics() {
    }

    public static List<Trail> detect(Collection<BlockPosition> positions, int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must not be negative");
        if (limit == 0 || positions.isEmpty()) return List.of();

        Map<BlockPosition, BlockPosition> indexed = new HashMap<>();
        for (BlockPosition position : positions) {
            indexed.putIfAbsent(position, position);
            if (indexed.size() == MAX_INPUT_POINTS) break;
        }
        Set<BlockPosition> visited = new HashSet<>();
        ArrayList<Trail> trails = new ArrayList<>();
        for (BlockPosition seed : indexed.values()) {
            if (!visited.add(seed)) continue;
            ArrayList<BlockPosition> component = new ArrayList<>();
            ArrayDeque<BlockPosition> frontier = new ArrayDeque<>();
            frontier.add(seed);
            while (!frontier.isEmpty() && component.size() < MAX_COMPONENT_POINTS) {
                BlockPosition current = frontier.removeFirst();
                component.add(current);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPosition neighbor = indexed.get(new BlockPosition(
                                current.x() + dx, current.y() + dy, current.z() + dz
                            ));
                            if (neighbor == null || visited.contains(neighbor)
                                || component.size() + frontier.size() >= MAX_COMPONENT_POINTS) continue;
                            if (visited.add(neighbor)) frontier.addLast(neighbor);
                        }
                    }
                }
            }
            Trail trail = summarize(component);
            if (trail != null) trails.add(trail);
        }

        trails.sort(Comparator.comparingInt(Trail::confidence).reversed()
            .thenComparing(Comparator.comparingInt(Trail::verticalSpan).reversed())
            .thenComparingInt(trail -> trail.lowest().x())
            .thenComparingInt(trail -> trail.lowest().z()));
        return List.copyOf(trails.subList(0, Math.min(limit, trails.size())));
    }

    private static Trail summarize(List<BlockPosition> component) {
        if (component.size() < 4) return null;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Set<Integer> levels = new HashSet<>();
        BlockPosition lowest = component.getFirst();
        for (BlockPosition point : component) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
            minZ = Math.min(minZ, point.z());
            maxZ = Math.max(maxZ, point.z());
            levels.add(point.y());
            if (point.y() < lowest.y()) lowest = point;
        }
        int verticalSpan = maxY - minY;
        if (verticalSpan < 4 || levels.size() < 4 || levels.size() * 100 < (verticalSpan + 1) * 60) return null;
        int horizontalSpan = Math.max(maxX - minX, maxZ - minZ);
        int confidence = Math.clamp(
            38 + Math.min(24, component.size() * 3) + Math.min(28, verticalSpan * 4)
                + (horizontalSpan > 0 ? 8 : 0),
            0,
            100
        );
        int strength = Math.min(200, 45 + component.size() * 5 + verticalSpan * 7);
        ArrayList<BlockPosition> ordered = new ArrayList<>(component);
        ordered.sort(Comparator.comparingInt(BlockPosition::y).reversed()
            .thenComparingInt(BlockPosition::x)
            .thenComparingInt(BlockPosition::z));
        return new Trail(lowest, confidence, strength, verticalSpan, horizontalSpan, List.copyOf(ordered));
    }

    public record Trail(
        BlockPosition lowest,
        int confidence,
        int strength,
        int verticalSpan,
        int horizontalSpan,
        List<BlockPosition> points
    ) {
        public Trail {
            points = List.copyOf(points);
        }
    }
}
