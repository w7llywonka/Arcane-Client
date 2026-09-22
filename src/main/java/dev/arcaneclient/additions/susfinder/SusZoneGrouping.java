package dev.arcaneclient.additions.susfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic bounded-radius zones; chaining never merges an arbitrarily long strip into one marker. */
public final class SusZoneGrouping {
    private SusZoneGrouping() { }

    public record Candidate(int chunkX, int chunkZ, int score, boolean inferred) {
        public double x() { return chunkX * 16.0 + 8; }
        public double z() { return chunkZ * 16.0 + 8; }
        public long key() { return ReceivedBlockLightCache.chunkKey(chunkX, chunkZ); }
    }
    public record Zone(double x, double z, int score, boolean inferred, List<Candidate> members) { }

    public static List<Zone> group(List<Candidate> candidates, int radius) {
        var sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(Candidate::score).reversed()
            .thenComparingInt(Candidate::chunkX).thenComparingInt(Candidate::chunkZ));
        var groups = new ArrayList<ArrayList<Candidate>>();
        double maxDistance = Math.max(0, radius) * (double)Math.max(0, radius);
        for (Candidate candidate : sorted) {
            ArrayList<Candidate> selected = null;
            double nearest = Double.MAX_VALUE;
            for (var group : groups) {
                // Complete linkage prevents bridge chunks from making one enormous, misleading zone.
                boolean fits = true;
                double distance = 0;
                for (var member : group) {
                    double dx = candidate.x() - member.x(), dz = candidate.z() - member.z();
                    double squared = dx * dx + dz * dz;
                    if (squared > maxDistance) { fits = false; break; }
                    distance += squared;
                }
                if (fits && distance / group.size() < nearest) {
                    nearest = distance / group.size(); selected = group;
                }
            }
            if (selected == null) { selected = new ArrayList<>(); groups.add(selected); }
            selected.add(candidate);
        }
        var zones = new ArrayList<Zone>();
        for (var group : groups) {
            double x = 0, z = 0;
            int score = 0;
            boolean inferred = false;
            for (var member : group) {
                x += member.x(); z += member.z(); score = Math.max(score, member.score()); inferred |= member.inferred();
            }
            zones.add(new Zone(x / group.size(), z / group.size(), score, inferred, List.copyOf(group)));
        }
        return List.copyOf(zones);
    }
}
