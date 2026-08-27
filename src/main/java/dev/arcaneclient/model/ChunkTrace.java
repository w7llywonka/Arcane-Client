package dev.arcaneclient.model;

import dev.arcaneclient.model.DepthProfile;
import dev.arcaneclient.model.ScanEvidence;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.ScoreSummary;
import dev.arcaneclient.model.SignalCategory;
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

@Environment(value=EnvType.CLIENT)
public final class ChunkTrace {
    private final EnumMap<SignalCategory, StaticHistory> staticHistory = new EnumMap<>(SignalCategory.class);
    private final Map<ScanEvidence.EvidenceKey, ScanEvidence> liveSignals = new HashMap<ScanEvidence.EvidenceKey, ScanEvidence>();

    public void merge(ScanResult scan) {
        EnumMap<SignalCategory, List<ScanEvidence>> staticByCategory = new EnumMap<>(SignalCategory.class);
        ArrayList<ScanEvidence> incomingLive = new ArrayList<ScanEvidence>();
        for (ScanEvidence scanEvidence : scan.evidence()) {
            if (scanEvidence.isLive()) {
                incomingLive.add(scanEvidence);
                continue;
            }
            staticByCategory.computeIfAbsent(scanEvidence.category(), ignored -> new ArrayList<>()).add(scanEvidence);
        }
        for (Map.Entry<SignalCategory, List<ScanEvidence>> entry : staticByCategory.entrySet()) {
            StaticHistory candidate = StaticHistory.from(entry.getValue());
            StaticHistory existing = this.staticHistory.get(entry.getKey());
            if (existing != null && !candidate.isPreferredTo(existing)) continue;
            this.staticHistory.put(entry.getKey(), candidate);
        }
        Set<ScanEvidence.EvidenceKey> staticKeys = this.allStaticKeys();
        this.liveSignals.keySet().removeAll(staticKeys);
        for (ScanEvidence evidence : incomingLive) {
            if (staticKeys.contains(evidence.key())) continue;
            this.liveSignals.merge(evidence.key(), evidence, ScanEvidence::mergeDuplicate);
        }
    }

    public int scoreAt(long tick) {
        return this.summarizeAt(tick).score();
    }

    public ScoreSummary summarizeAt(long tick) {
        return this.summarizeAt(tick, ignored -> true, true, false);
    }

    public ScoreSummary summarizeAt(long tick, Predicate<SignalCategory> categoryEnabled, boolean includeLiveSignals) {
        return this.summarizeAt(tick, categoryEnabled, includeLiveSignals, false);
    }

    public ScoreSummary summarizeAt(long tick, Predicate<SignalCategory> categoryEnabled, boolean includeLiveSignals, boolean deepFocus) {
        EnumMap<SignalCategory, Integer> rawStrengths = new EnumMap<SignalCategory, Integer>(SignalCategory.class);
        ArrayList<String> reasons = new ArrayList<String>();
        boolean hasDeepAnchor = false;
        for (Map.Entry<SignalCategory, StaticHistory> entry : this.staticHistory.entrySet()) {
            if (!categoryEnabled.test(entry.getKey())) continue;
            for (ScanEvidence evidence : entry.getValue().evidence()) {
                ChunkTrace.addSaturated(rawStrengths, evidence.category(), ChunkTrace.adjustedStrength(evidence, evidence.strength(), deepFocus));
                reasons.add(evidence.reason());
                hasDeepAnchor |= evidence.position().y() <= 48;
            }
        }
        for (ScanEvidence evidence : this.liveSignals.values()) {
            int decayedStrength;
            if (!includeLiveSignals || !categoryEnabled.test(evidence.category()) || (decayedStrength = evidence.strengthAt(tick)) <= 0) continue;
            ChunkTrace.addSaturated(rawStrengths, evidence.category(), ChunkTrace.adjustedStrength(evidence, decayedStrength, deepFocus));
            reasons.add(evidence.reason());
            hasDeepAnchor |= evidence.position().y() <= 48;
        }
        if (deepFocus && !hasDeepAnchor) {
            return ScoreSummary.fromRawStrengths(Map.of(), List.of());
        }
        return ScoreSummary.fromRawStrengths(rawStrengths, reasons);
    }

    private static int adjustedStrength(ScanEvidence evidence, int strength, boolean deepFocus) {
        return deepFocus ? DepthProfile.adjust(strength, evidence.position().y()) : strength;
    }

    public int historicalRawMaximum(SignalCategory category) {
        StaticHistory history = this.staticHistory.get(category);
        return history == null ? 0 : history.rawStrength();
    }

    public int liveSignalCount() {
        return this.liveSignals.size();
    }

    public boolean hasActiveEvidenceAtOrBelow(long tick, Predicate<SignalCategory> categoryEnabled, boolean includeLiveSignals, int maximumY) {
        for (Map.Entry<SignalCategory, StaticHistory> entry : this.staticHistory.entrySet()) {
            if (!categoryEnabled.test(entry.getKey()) || !entry.getValue().evidence().stream().anyMatch(evidence -> evidence.position().y() <= maximumY)) continue;
            return true;
        }
        if (!includeLiveSignals) {
            return false;
        }
        return this.liveSignals.values().stream().anyMatch(evidence -> categoryEnabled.test(evidence.category()) && evidence.position().y() <= maximumY && evidence.strengthAt(tick) > 0);
    }

    private Set<ScanEvidence.EvidenceKey> allStaticKeys() {
        HashSet<ScanEvidence.EvidenceKey> keys = new HashSet<ScanEvidence.EvidenceKey>();
        for (StaticHistory history : this.staticHistory.values()) {
            for (ScanEvidence evidence : history.evidence()) {
                keys.add(evidence.key());
            }
        }
        return keys;
    }

    private static void addSaturated(EnumMap<SignalCategory, Integer> totals, SignalCategory category, int amount) {
        long sum = (long)totals.getOrDefault(category, 0).intValue() + (long)amount;
        totals.put(category, (int)Math.min(Integer.MAX_VALUE, sum));
    }

    @Environment(value=EnvType.CLIENT)
    private record StaticHistory(int rawStrength, List<ScanEvidence> evidence) {
        static StaticHistory from(List<ScanEvidence> evidence) {
            ArrayList<ScanEvidence> sorted = new ArrayList<ScanEvidence>(evidence);
            sorted.sort(ScanEvidence.ORDER);
            long raw = 0L;
            for (ScanEvidence item : sorted) {
                raw = Math.min(Integer.MAX_VALUE, raw + (long)item.strength());
            }
            return new StaticHistory((int)raw, List.copyOf(sorted));
        }

        boolean isPreferredTo(StaticHistory other) {
            if (this.rawStrength != other.rawStrength) {
                return this.rawStrength > other.rawStrength;
            }
            return StaticHistory.compareEvidence(this.evidence, other.evidence) < 0;
        }

        private static int compareEvidence(List<ScanEvidence> left, List<ScanEvidence> right) {
            int sharedLength = Math.min(left.size(), right.size());
            for (int index = 0; index < sharedLength; ++index) {
                int compared = ScanEvidence.ORDER.compare(left.get(index), right.get(index));
                if (compared == 0) continue;
                return compared;
            }
            return Integer.compare(left.size(), right.size());
        }
    }
}
