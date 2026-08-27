package dev.arcaneclient.model;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ScanEvidence;
import dev.arcaneclient.model.ScoreSummary;
import dev.arcaneclient.model.SignalCategory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class ScanResult {
    private final List<ScanEvidence> evidence;
    private final ScoreSummary summary;

    private ScanResult(Collection<ScanEvidence> evidence) {
        ArrayList<ScanEvidence> sorted = new ArrayList<ScanEvidence>(evidence);
        sorted.sort(ScanEvidence.ORDER);
        this.evidence = List.copyOf(sorted);
        EnumMap<SignalCategory, Integer> rawStrengths = new EnumMap<SignalCategory, Integer>(SignalCategory.class);
        ArrayList<String> reasons = new ArrayList<String>();
        for (ScanEvidence item : sorted) {
            ScanResult.addSaturated(rawStrengths, item.category(), item.strength());
            reasons.add(item.reason());
        }
        this.summary = ScoreSummary.fromRawStrengths(rawStrengths, reasons);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ScanEvidence> evidence() {
        return this.evidence;
    }

    public int score() {
        return this.summary.score();
    }

    public boolean suspicious() {
        return this.summary.suspicious();
    }

    public int synergy() {
        return this.summary.synergy();
    }

    public Map<SignalCategory, Integer> categoryBreakdown() {
        return this.summary.categoryBreakdown();
    }

    public List<String> reasons() {
        return this.summary.reasons();
    }

    public ScoreSummary summary() {
        return this.summary;
    }

    private static void addSaturated(EnumMap<SignalCategory, Integer> totals, SignalCategory category, int amount) {
        long sum = (long)totals.getOrDefault((Object)category, 0).intValue() + (long)amount;
        totals.put(category, (int)Math.min(Integer.MAX_VALUE, sum));
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Builder {
        private final Map<ScanEvidence.EvidenceKey, ScanEvidence> evidence = new HashMap<ScanEvidence.EvidenceKey, ScanEvidence>();

        public Builder add(ScanEvidence item) {
            this.evidence.merge(item.key(), item, ScanEvidence::mergeDuplicate);
            return this;
        }

        public Builder addStatic(SignalCategory category, BlockPosition position, String reason, int strength) {
            return this.add(ScanEvidence.staticSignal(category, position, reason, strength));
        }

        public Builder addLive(SignalCategory category, BlockPosition position, String reason, int strength, long observedAtTick, int decayTicks) {
            return this.add(ScanEvidence.liveSignal(category, position, reason, strength, observedAtTick, decayTicks));
        }

        public ScanResult build() {
            return new ScanResult(this.evidence.values());
        }
    }
}
