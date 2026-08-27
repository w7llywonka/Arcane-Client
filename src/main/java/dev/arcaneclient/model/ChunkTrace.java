package dev.arcaneclient.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ChunkTrace {
    private final EnumMap<SignalCategory, StaticHistory> staticHistory = new EnumMap<>(SignalCategory.class);
    private final Map<ScanEvidence.EvidenceKey, ScanEvidence> liveSignals = new HashMap<>();

    public void mergeSnapshot(ScanResult scan) {
        this.staticHistory.clear();
        this.merge(scan);
    }

    public void merge(ScanResult scan) {
        EnumMap<SignalCategory, List<ScanEvidence>> staticByCategory = new EnumMap<>(SignalCategory.class);
        ArrayList<ScanEvidence> incomingLive = new ArrayList<>();
        for (ScanEvidence evidence : scan.evidence()) {
            if (evidence.isLive()) {
                incomingLive.add(evidence);
            } else {
                staticByCategory.computeIfAbsent(evidence.category(), ignored -> new ArrayList<>()).add(evidence);
            }
        }

        for (Map.Entry<SignalCategory, List<ScanEvidence>> entry : staticByCategory.entrySet()) {
            StaticHistory candidate = StaticHistory.from(entry.getValue());
            StaticHistory existing = this.staticHistory.get(entry.getKey());
            if (existing == null || candidate.isPreferredTo(existing)) {
                this.staticHistory.put(entry.getKey(), candidate);
            }
        }

        Set<ScanEvidence.EvidenceKey> staticKeys = this.allStaticKeys();
        this.liveSignals.keySet().removeAll(staticKeys);
        for (ScanEvidence evidence : incomingLive) {
            if (!staticKeys.contains(evidence.key())) {
                this.liveSignals.merge(evidence.key(), evidence, ScanEvidence::mergeDuplicate);
            }
        }
    }

    public int scoreAt(long tick) {
        return this.summarizeAt(tick).score();
    }

    public ScoreSummary summarizeAt(long tick) {
        return this.summarizeAt(tick, ignored -> true, true);
    }

    public ScoreSummary summarizeAt(
        long tick,
        Predicate<SignalCategory> categoryEnabled,
        boolean includeLiveSignals
    ) {
        EnumMap<SignalCategory, Integer> rawStrengths = new EnumMap<>(SignalCategory.class);
        ArrayList<String> reasons = new ArrayList<>();

        for (Map.Entry<SignalCategory, StaticHistory> entry : this.staticHistory.entrySet()) {
            if (!categoryEnabled.test(entry.getKey())) {
                continue;
            }
            for (ScanEvidence evidence : entry.getValue().evidence()) {
                addSaturated(rawStrengths, evidence.category(), evidence.strength());
                reasons.add(evidence.reason());
            }
        }

        if (includeLiveSignals) {
            for (ScanEvidence evidence : this.liveSignals.values()) {
                if (!categoryEnabled.test(evidence.category())) {
                    continue;
                }
                int decayedStrength = evidence.strengthAt(tick);
                if (decayedStrength <= 0) {
                    continue;
                }
                addSaturated(rawStrengths, evidence.category(), decayedStrength);
                reasons.add(evidence.reason());
            }
        }

        return ScoreSummary.fromRawStrengths(rawStrengths, reasons);
    }

    public int historicalRawMaximum(SignalCategory category) {
        StaticHistory history = this.staticHistory.get(category);
        return history == null ? 0 : history.rawStrength();
    }

    public int liveSignalCount() {
        return this.liveSignals.size();
    }

    private Set<ScanEvidence.EvidenceKey> allStaticKeys() {
        HashSet<ScanEvidence.EvidenceKey> keys = new HashSet<>();
        for (StaticHistory history : this.staticHistory.values()) {
            for (ScanEvidence evidence : history.evidence()) {
                keys.add(evidence.key());
            }
        }
        return keys;
    }

    private static void addSaturated(
        EnumMap<SignalCategory, Integer> totals,
        SignalCategory category,
        int amount
    ) {
        long sum = (long) totals.getOrDefault(category, 0) + amount;
        totals.put(category, (int) Math.min(Integer.MAX_VALUE, sum));
    }

    @Environment(EnvType.CLIENT)
    private record StaticHistory(int rawStrength, List<ScanEvidence> evidence) {
        private static StaticHistory from(List<ScanEvidence> evidence) {
            ArrayList<ScanEvidence> sorted = new ArrayList<>(evidence);
            sorted.sort(ScanEvidence.ORDER);
            long raw = 0L;
            for (ScanEvidence item : sorted) {
                raw = Math.min(Integer.MAX_VALUE, raw + item.strength());
            }
            return new StaticHistory((int) raw, List.copyOf(sorted));
        }

        private boolean isPreferredTo(StaticHistory other) {
            if (this.rawStrength != other.rawStrength) {
                return this.rawStrength > other.rawStrength;
            }
            return compareEvidence(this.evidence, other.evidence) < 0;
        }

        private static int compareEvidence(List<ScanEvidence> left, List<ScanEvidence> right) {
            int sharedLength = Math.min(left.size(), right.size());
            for (int index = 0; index < sharedLength; index++) {
                int compared = ScanEvidence.ORDER.compare(left.get(index), right.get(index));
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(left.size(), right.size());
        }
    }
}
