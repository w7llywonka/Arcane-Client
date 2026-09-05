package dev.arcaneclient.model;

import dev.arcaneclient.model.ChunkIntel;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.EvidencePoint;
import dev.arcaneclient.model.ScanEvidence;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.ScoreSummary;
import dev.arcaneclient.model.SignalCategory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int MAX_COMPLETE_SNAPSHOTS = 6;
    private static final int STATIC_FRESH_TICKS = 6000;
    private static final int STATIC_EXPIRE_TICKS = 36000;
    private static final int MAX_EVIDENCE_POINTS = 64;

    @FunctionalInterface
    public interface EvidenceFilter {
        boolean test(ScanEvidence evidence);
    }

    private final EnumMap<SignalCategory, StaticHistory> staticHistory = new EnumMap<>(SignalCategory.class);
    private final EnumMap<SignalCategory, StaticHistory> legacyStaticHistory = new EnumMap<>(SignalCategory.class);
    private final Map<ScanEvidence.EvidenceKey, ScanEvidence> liveSignals = new HashMap<ScanEvidence.EvidenceKey, ScanEvidence>();
    private final Map<ScanEvidence.EvidenceKey, Integer> liveObservations = new HashMap<ScanEvidence.EvidenceKey, Integer>();
    private final EnumMap<SignalCategory, Long> staticLastSeen = new EnumMap<>(SignalCategory.class);
    private final ArrayDeque<StaticSnapshot> completeSnapshots = new ArrayDeque<StaticSnapshot>();

    public void merge(ScanResult scan) {
        this.merge(scan, scan.observedAtTick());
    }

    public void merge(ScanResult scan, long tick) {
        long observedAt = Math.max(tick, scan.observedAtTick());
        EnumMap<SignalCategory, List<ScanEvidence>> staticByCategory = new EnumMap<>(SignalCategory.class);
        ArrayList<ScanEvidence> incomingStatic = new ArrayList<ScanEvidence>();
        ArrayList<ScanEvidence> incomingLive = new ArrayList<ScanEvidence>();
        for (ScanEvidence scanEvidence : scan.evidence()) {
            if (scanEvidence.isLive()) {
                incomingLive.add(scanEvidence);
                continue;
            }
            incomingStatic.add(scanEvidence);
        }
        EnumMap<SignalCategory, List<ScanEvidence>> legacyByCategory = new EnumMap<>(SignalCategory.class);
        for (ScanEvidence evidence : incomingStatic) {
            legacyByCategory.computeIfAbsent(evidence.category(), ignored -> new ArrayList<>()).add(evidence);
        }
        for (Map.Entry<SignalCategory, List<ScanEvidence>> entry : legacyByCategory.entrySet()) {
            StaticHistory candidate = StaticHistory.from(entry.getValue());
            StaticHistory existing = this.legacyStaticHistory.get(entry.getKey());
            if (existing == null || candidate.isPreferredTo(existing)) {
                this.legacyStaticHistory.put(entry.getKey(), candidate);
            }
        }
        for (ScanEvidence evidence : capStrongest(incomingStatic, MAX_EVIDENCE_POINTS)) {
            staticByCategory.computeIfAbsent(evidence.category(), ignored -> new ArrayList<>()).add(evidence);
        }
        if (scan.completeSnapshot()) {
            this.completeSnapshots.addLast(new StaticSnapshot(observedAt, scan.coveragePercent(), List.copyOf(
                staticByCategory.values().stream().flatMap(List::stream).toList()
            )));
            while (this.completeSnapshots.size() > MAX_COMPLETE_SNAPSHOTS) {
                this.completeSnapshots.removeFirst();
            }
            this.staticHistory.clear();
            this.staticLastSeen.clear();
            for (Map.Entry<SignalCategory, List<ScanEvidence>> entry : staticByCategory.entrySet()) {
                this.staticHistory.put(entry.getKey(), StaticHistory.from(entry.getValue()));
                this.staticLastSeen.put(entry.getKey(), observedAt);
            }
        } else {
            for (Map.Entry<SignalCategory, List<ScanEvidence>> entry : staticByCategory.entrySet()) {
                StaticHistory candidate = StaticHistory.from(entry.getValue());
                StaticHistory existing = this.staticHistory.get(entry.getKey());
                if (existing != null && !candidate.isPreferredTo(existing)) continue;
                this.staticHistory.put(entry.getKey(), candidate);
                this.staticLastSeen.put(entry.getKey(), observedAt);
            }
        }
        Set<ScanEvidence.EvidenceKey> staticKeys = this.allStaticKeys();
        this.liveSignals.keySet().removeAll(staticKeys);
        this.liveObservations.keySet().removeAll(staticKeys);
        for (ScanEvidence evidence : incomingLive) {
            if (staticKeys.contains(evidence.key())) continue;
            this.liveSignals.merge(evidence.key(), evidence, ScanEvidence::mergeDuplicate);
            this.liveObservations.merge(evidence.key(), 1, (left, right) -> Math.min(Integer.MAX_VALUE, left + right));
        }
        this.liveSignals.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().strengthAt(observedAt) <= 0;
            if (expired) this.liveObservations.remove(entry.getKey());
            return expired;
        });
        this.trimLiveSignals(observedAt);
    }

    public int scoreAt(long tick) {
        return this.summarizeAt(tick).score();
    }

    public ChunkIntel intelligenceAt(long tick) {
        return this.intelligenceAt(tick, ignored -> true, true);
    }

    public ChunkIntel intelligenceAt(long tick, Predicate<SignalCategory> categoryEnabled, boolean includeLiveSignals) {
        return this.intelligenceEvidenceAt(tick, evidence -> categoryEnabled.test(evidence.category()), includeLiveSignals);
    }

    public ChunkIntel intelligenceEvidenceAt(long tick, EvidenceFilter evidenceEnabled, boolean includeLiveSignals) {
        ArrayList<EvidencePoint> points = new ArrayList<EvidencePoint>();
        HashSet<EvidenceFamily> families = new HashSet<EvidenceFamily>();
        int observations = this.completeSnapshots.size();
        long newest = Long.MIN_VALUE;
        this.appendStaticPoints(points, families, tick, evidenceEnabled);
        if (includeLiveSignals) {
            observations += this.appendLivePoints(points, families, tick, evidenceEnabled);
        }
        for (long seen : this.staticLastSeen.values()) newest = Math.max(newest, seen);
        for (ScanEvidence evidence : this.liveSignals.values()) {
            if (evidence.strengthAt(tick) > 0) newest = Math.max(newest, evidence.observedAtTick());
        }
        points.sort(Comparator.comparingInt(EvidencePoint::strength).reversed()
            .thenComparing(point -> point.family().ordinal())
            .thenComparingInt(point -> point.position().x())
            .thenComparingInt(point -> point.position().y())
            .thenComparingInt(point -> point.position().z()));
        if (points.size() > MAX_EVIDENCE_POINTS) {
            points.subList(MAX_EVIDENCE_POINTS, points.size()).clear();
        }
        int coverage = this.completeSnapshots.isEmpty() ? (points.isEmpty() ? 0 : 100) : this.completeSnapshots.getLast().coveragePercent();
        int freshness = newest == Long.MIN_VALUE ? 0 : staticFreshness(tick, newest);
        int confidence = confidence(this.summarizeEvidenceAt(tick, evidenceEnabled, includeLiveSignals).score(), observations, coverage, freshness, families.size());
        return new ChunkIntel(confidence, observations, this.completeSnapshots.size(), coverage, freshness, families.size(), points);
    }

    public ScoreSummary summarizeAt(long tick) {
        return this.summarizeAt(tick, ignored -> true, true);
    }

    public ScoreSummary summarizeAt(long tick, Predicate<SignalCategory> categoryEnabled, boolean includeLiveSignals) {
        return this.summarizeEvidenceAt(tick, evidence -> categoryEnabled.test(evidence.category()), includeLiveSignals);
    }

    public ScoreSummary summarizeEvidenceAt(long tick, EvidenceFilter evidenceEnabled, boolean includeLiveSignals) {
        EnumMap<SignalCategory, Integer> rawStrengths = new EnumMap<SignalCategory, Integer>(SignalCategory.class);
        EnumMap<EvidenceFamily, Integer> familyStrengths = new EnumMap<EvidenceFamily, Integer>(EvidenceFamily.class);
        ArrayList<String> reasons = new ArrayList<String>();
        for (Map.Entry<SignalCategory, StaticHistory> entry : this.staticHistory.entrySet()) {
            int freshness = staticFreshness(tick, this.staticLastSeen.getOrDefault(entry.getKey(), tick));
            if (freshness <= 0) continue;
            for (ScanEvidence evidence : entry.getValue().evidence()) {
                if (!evidenceEnabled.test(evidence)) continue;
                int observations = this.staticObservations(evidence);
                int confirmed = Math.min(125, 100 + Math.max(0, observations - 1) * 5);
                int strength = evidence.strength() * freshness / 100 * confirmed / 100;
                ChunkTrace.addFamilyCapped(rawStrengths, familyStrengths, evidence, strength);
                reasons.add(evidence.reason());
            }
        }
        for (ScanEvidence evidence : this.liveSignals.values()) {
            int decayedStrength;
            if (!includeLiveSignals || !evidenceEnabled.test(evidence) || (decayedStrength = evidence.strengthAt(tick)) <= 0) continue;
            ChunkTrace.addFamilyCapped(rawStrengths, familyStrengths, evidence, decayedStrength);
            reasons.add(evidence.reason());
        }
        return ScoreSummary.fromRawStrengths(rawStrengths, reasons, familyStrengths.size());
    }

    /**
     * Scores evidence using the archived 1.6/1.8 contract. This deliberately
     * keeps the strongest static observation per category, applies the legacy
     * vertical profile, and awards synergy by independent score category.
     */
    public ScoreSummary summarizeLegacyAt(
        long tick,
        EvidenceFilter evidenceEnabled,
        boolean includeLiveSignals,
        boolean deepFocus
    ) {
        EnumMap<SignalCategory, Integer> rawStrengths = new EnumMap<>(SignalCategory.class);
        ArrayList<String> reasons = new ArrayList<>();
        boolean deepAnchor = false;
        for (StaticHistory history : this.legacyStaticHistory.values()) {
            for (ScanEvidence evidence : history.evidence()) {
                if (!evidenceEnabled.test(evidence)) continue;
                int strength = deepFocus
                    ? DepthProfile.adjust(evidence.strength(), evidence.position().y())
                    : evidence.strength();
                addSaturated(rawStrengths, evidence.category(), strength);
                reasons.add(evidence.reason());
                deepAnchor |= evidence.position().y() <= 48;
            }
        }
        if (includeLiveSignals) {
            for (ScanEvidence evidence : this.liveSignals.values()) {
                int strength = evidence.strengthAt(tick);
                if (strength <= 0 || !evidenceEnabled.test(evidence)) continue;
                addSaturated(
                    rawStrengths,
                    evidence.category(),
                    deepFocus ? DepthProfile.adjust(strength, evidence.position().y()) : strength
                );
                reasons.add(evidence.reason());
                deepAnchor |= evidence.position().y() <= 48;
            }
        }
        if (deepFocus && !deepAnchor) {
            return ScoreSummary.fromRawStrengths(Map.of(), List.of());
        }
        return ScoreSummary.fromRawStrengths(rawStrengths, reasons);
    }

    private void appendStaticPoints(
        List<EvidencePoint> points,
        Set<EvidenceFamily> families,
        long tick,
        EvidenceFilter evidenceEnabled
    ) {
        for (Map.Entry<SignalCategory, StaticHistory> entry : this.staticHistory.entrySet()) {
            long seen = this.staticLastSeen.getOrDefault(entry.getKey(), tick);
            int freshness = staticFreshness(tick, seen);
            if (freshness <= 0) continue;
            for (ScanEvidence evidence : entry.getValue().evidence()) {
                if (!evidenceEnabled.test(evidence)) continue;
                int strength = Math.max(1, evidence.strength() * freshness / 100);
                int observations = this.staticObservations(evidence);
                points.add(new EvidencePoint(evidence.category(), evidence.family(), evidence.position(), evidence.reason(), strength, seen, observations, false));
                families.add(evidence.family());
            }
        }
    }

    private int appendLivePoints(
        List<EvidencePoint> points,
        Set<EvidenceFamily> families,
        long tick,
        EvidenceFilter evidenceEnabled
    ) {
        int observations = 0;
        for (Map.Entry<ScanEvidence.EvidenceKey, ScanEvidence> entry : this.liveSignals.entrySet()) {
            ScanEvidence evidence = entry.getValue();
            int strength = evidence.strengthAt(tick);
            if (strength <= 0 || !evidenceEnabled.test(evidence)) continue;
            int seen = this.liveObservations.getOrDefault(entry.getKey(), 1);
            observations += seen;
            points.add(new EvidencePoint(evidence.category(), evidence.family(), evidence.position(), evidence.reason(), strength, evidence.observedAtTick(), seen, true));
            families.add(evidence.family());
        }
        return observations;
    }

    private static int staticFreshness(long tick, long observedAtTick) {
        long age = Math.max(0L, tick - observedAtTick);
        if (age <= STATIC_FRESH_TICKS) return 100;
        if (age >= STATIC_EXPIRE_TICKS) return 0;
        return (int)((STATIC_EXPIRE_TICKS - age) * 100L / (STATIC_EXPIRE_TICKS - STATIC_FRESH_TICKS));
    }

    private static int confidence(int score, int observations, int coverage, int freshness, int families) {
        if (score <= 0 || families <= 0) return 0;
        int value = score / 2
            + Math.min(15, observations * 3)
            + coverage / 10
            + freshness / 10
            + Math.min(15, Math.max(0, families - 1) * 5);
        return Math.min(families == 1 ? 65 : 100, value);
    }

    private int staticObservations(ScanEvidence evidence) {
        int observations = 0;
        for (StaticSnapshot snapshot : this.completeSnapshots) {
            if (snapshot.contains(evidence.key())) ++observations;
        }
        return Math.max(1, observations);
    }

    private static void addFamilyCapped(
        EnumMap<SignalCategory, Integer> totals,
        EnumMap<EvidenceFamily, Integer> familyTotals,
        ScanEvidence evidence,
        int amount
    ) {
        if (amount <= 0) return;
        int used = familyTotals.getOrDefault(evidence.family(), 0);
        int accepted = Math.min(amount, Math.max(0, evidence.family().rawStrengthCap() - used));
        if (accepted <= 0) return;
        familyTotals.put(evidence.family(), used + accepted);
        addSaturated(totals, evidence.category(), accepted);
    }

    public int historicalRawMaximum(SignalCategory category) {
        StaticHistory history = this.staticHistory.get(category);
        int maximum = history == null ? 0 : history.rawStrength();
        StaticHistory legacy = this.legacyStaticHistory.get(category);
        if (legacy != null) maximum = Math.max(maximum, legacy.rawStrength());
        for (StaticSnapshot snapshot : this.completeSnapshots) {
            maximum = Math.max(maximum, snapshot.rawStrength(category));
        }
        return maximum;
    }

    public int liveSignalCount() {
        return this.liveSignals.size();
    }

    public boolean hasActiveEvidenceAtOrBelow(long tick, Predicate<SignalCategory> categoryEnabled, boolean includeLiveSignals, int maximumY) {
        return this.hasActiveEvidenceAtOrBelowEvidence(
            tick, evidence -> categoryEnabled.test(evidence.category()), includeLiveSignals, maximumY
        );
    }

    public boolean hasActiveEvidenceAtOrBelowEvidence(long tick, EvidenceFilter evidenceEnabled, boolean includeLiveSignals, int maximumY) {
        for (Map.Entry<SignalCategory, StaticHistory> entry : this.staticHistory.entrySet()) {
            if (staticFreshness(tick, this.staticLastSeen.getOrDefault(entry.getKey(), tick)) <= 0
                || !entry.getValue().evidence().stream().anyMatch(evidence ->
                    evidenceEnabled.test(evidence) && evidence.position().y() <= maximumY)) continue;
            return true;
        }
        if (!includeLiveSignals) {
            return false;
        }
        return this.liveSignals.values().stream().anyMatch(evidence ->
            evidenceEnabled.test(evidence) && evidence.position().y() <= maximumY && evidence.strengthAt(tick) > 0);
    }

    public boolean legacyHasActiveEvidenceAtOrBelow(
        long tick,
        EvidenceFilter evidenceEnabled,
        boolean includeLiveSignals,
        int maximumY
    ) {
        for (StaticHistory history : this.legacyStaticHistory.values()) {
            if (history.evidence().stream().anyMatch(evidence ->
                evidenceEnabled.test(evidence) && evidence.position().y() <= maximumY)) {
                return true;
            }
        }
        return includeLiveSignals && this.liveSignals.values().stream().anyMatch(evidence ->
            evidenceEnabled.test(evidence)
                && evidence.position().y() <= maximumY
                && evidence.strengthAt(tick) > 0
        );
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

    private static List<ScanEvidence> capStrongest(List<ScanEvidence> evidence, int limit) {
        if (evidence.size() <= limit) return List.copyOf(evidence);
        ArrayList<ScanEvidence> strongest = new ArrayList<ScanEvidence>(evidence);
        strongest.sort(Comparator.comparingInt(ScanEvidence::strength).reversed().thenComparing(ScanEvidence.ORDER));
        strongest.subList(limit, strongest.size()).clear();
        return List.copyOf(strongest);
    }

    private void trimLiveSignals(long tick) {
        if (this.liveSignals.size() <= MAX_EVIDENCE_POINTS) return;
        ArrayList<Map.Entry<ScanEvidence.EvidenceKey, ScanEvidence>> weakest =
            new ArrayList<Map.Entry<ScanEvidence.EvidenceKey, ScanEvidence>>(this.liveSignals.entrySet());
        weakest.sort(Comparator
            .comparingInt((Map.Entry<ScanEvidence.EvidenceKey, ScanEvidence> entry) -> entry.getValue().strengthAt(tick))
            .thenComparingLong(entry -> entry.getValue().observedAtTick()));
        int remove = weakest.size() - MAX_EVIDENCE_POINTS;
        for (int index = 0; index < remove; ++index) {
            ScanEvidence.EvidenceKey key = weakest.get(index).getKey();
            this.liveSignals.remove(key);
            this.liveObservations.remove(key);
        }
    }

    private static void addSaturated(EnumMap<SignalCategory, Integer> totals, SignalCategory category, int amount) {
        long sum = (long)totals.getOrDefault(category, 0).intValue() + (long)amount;
        totals.put(category, (int)Math.min(Integer.MAX_VALUE, sum));
    }

    @Environment(value=EnvType.CLIENT)
    private record StaticSnapshot(long observedAtTick, int coveragePercent, List<ScanEvidence> evidence) {
        StaticSnapshot {
            coveragePercent = Math.max(0, Math.min(100, coveragePercent));
            evidence = List.copyOf(evidence);
        }

        boolean contains(ScanEvidence.EvidenceKey key) {
            return this.evidence.stream().anyMatch(item -> item.key().equals(key));
        }

        int rawStrength(SignalCategory category) {
            long total = 0L;
            for (ScanEvidence item : this.evidence) {
                if (item.category() == category) total = Math.min(Integer.MAX_VALUE, total + item.strength());
            }
            return (int)total;
        }
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
