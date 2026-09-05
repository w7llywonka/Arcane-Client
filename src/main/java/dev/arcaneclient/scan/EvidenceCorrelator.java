package dev.arcaneclient.scan;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.ScanEvidence;
import dev.arcaneclient.model.SignalCategory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Bounded packet-event correlation. A lone block update never becomes high-confidence evidence. */
@Environment(EnvType.CLIENT)
public final class EvidenceCorrelator {
    static final int MAX_TRACKED_CHUNKS = 2048;
    private static final int MAX_EVENTS_PER_STREAM = 96;
    private final LinkedHashMap<Long, ChunkEvents> chunks = new LinkedHashMap<>(256, 0.75F, true);

    public List<ScanEvidence> record(
        BlockPosition position,
        long tick,
        GrowthTransitions.GrowthEvent growth,
        boolean automationChanged
    ) {
        long key = chunkKey(position.chunkX(), position.chunkZ());
        ChunkEvents events = this.chunks.computeIfAbsent(key, ignored -> new ChunkEvents());
        ArrayList<ScanEvidence> correlated = new ArrayList<>(3);
        events.prune(tick);
        if (growth != null) {
            events.recordGrowth(position, tick, growth, correlated);
        }
        if (automationChanged) {
            events.recordAutomation(position, tick, correlated);
        }
        while (this.chunks.size() > MAX_TRACKED_CHUNKS) {
            this.chunks.remove(this.chunks.entrySet().iterator().next().getKey());
        }
        return List.copyOf(correlated);
    }

    public void forgetChunk(int chunkX, int chunkZ) {
        this.chunks.remove(chunkKey(chunkX, chunkZ));
    }

    public void reset() {
        this.chunks.clear();
    }

    int trackedChunks() {
        return this.chunks.size();
    }

    private static boolean isHarvest(GrowthTransitions.GrowthEvent event) {
        if (event.category() != SignalCategory.LIVE_ACTIVITY) return false;
        return switch (event.family()) {
            case KELP, BERRY, CAVE_VINE, CROP, VERTICAL_PLANT -> true;
            default -> false;
        };
    }

    private static Set<BlockPosition> unique(ArrayDeque<TimedPosition> events) {
        HashSet<BlockPosition> positions = new HashSet<BlockPosition>();
        for (TimedPosition event : events) positions.add(event.position());
        return positions;
    }

    private static long span(ArrayDeque<TimedPosition> events) {
        return events.size() < 2 ? 0L : Math.max(0L, events.getLast().tick() - events.getFirst().tick());
    }

    private static int maxAxisRun(Set<BlockPosition> positions) {
        int best = 0;
        for (BlockPosition start : positions) {
            int xRun = 1;
            while (positions.contains(new BlockPosition(start.x() + xRun, start.y(), start.z()))) ++xRun;
            int zRun = 1;
            while (positions.contains(new BlockPosition(start.x(), start.y(), start.z() + zRun))) ++zRun;
            best = Math.max(best, Math.max(xRun, zRun));
        }
        return best;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long)chunkX & 0xFFFFFFFFL | (long)chunkZ << 32;
    }

    private record TimedPosition(BlockPosition position, long tick) {
    }

    private static final class ChunkEvents {
        private final ArrayDeque<TimedPosition> harvest = new ArrayDeque<>();
        private final ArrayDeque<TimedPosition> growth = new ArrayDeque<>();
        private final ArrayDeque<TimedPosition> automation = new ArrayDeque<>();
        private long lastHarvestSignal = Long.MIN_VALUE;
        private long lastGrowthSignal = Long.MIN_VALUE;
        private long lastAutomationSignal = Long.MIN_VALUE;
        private int harvestBursts;

        void recordGrowth(
            BlockPosition position,
            long tick,
            GrowthTransitions.GrowthEvent event,
            List<ScanEvidence> output
        ) {
            if (isHarvest(event)) {
                addBounded(this.harvest, new TimedPosition(position, tick));
                Set<BlockPosition> unique = unique(this.harvest);
                int strength = EvidenceHeuristics.harvestRhythm(unique.size(), maxAxisRun(unique), span(this.harvest), this.harvestBursts);
                if (strength > 0 && elapsed(tick, this.lastHarvestSignal) >= 80L) {
                    ++this.harvestBursts;
                    this.lastHarvestSignal = tick;
                    output.add(ScanEvidence.liveSignal(
                        SignalCategory.LIVE_ACTIVITY, position, "coordinated harvest rhythm",
                        strength, tick, 12000, EvidenceFamily.HARVEST
                    ));
                }
                return;
            }
            if (event.category() != SignalCategory.NATURAL_GROWTH) return;
            addBounded(this.growth, new TimedPosition(position, tick));
            Set<BlockPosition> unique = unique(this.growth);
            int strength = EvidenceHeuristics.growthChronicle(this.growth.size(), unique.size(), span(this.growth));
            if (strength > 0 && elapsed(tick, this.lastGrowthSignal) >= 200L) {
                this.lastGrowthSignal = tick;
                output.add(ScanEvidence.liveSignal(
                    SignalCategory.NATURAL_GROWTH, position, "repeated growth chronicle",
                    strength, tick, 12000, EvidenceFamily.GROWTH
                ));
            }
        }

        void recordAutomation(BlockPosition position, long tick, List<ScanEvidence> output) {
            addBounded(this.automation, new TimedPosition(position, tick));
            Set<BlockPosition> unique = unique(this.automation);
            int strength = EvidenceHeuristics.automationCadence(this.automation.size(), unique.size(), span(this.automation));
            if (strength <= 0 || elapsed(tick, this.lastAutomationSignal) < 100L) return;
            this.lastAutomationSignal = tick;
            output.add(ScanEvidence.liveSignal(
                SignalCategory.INTERACTION, position, "multi-position automation cadence",
                strength, tick, 12000, EvidenceFamily.AUTOMATION
            ));
        }

        void prune(long tick) {
            prune(this.harvest, tick - 100L);
            prune(this.automation, tick - 120L);
            prune(this.growth, tick - 2400L);
        }

        private static void prune(ArrayDeque<TimedPosition> events, long minimumTick) {
            while (!events.isEmpty() && events.getFirst().tick() < minimumTick) events.removeFirst();
        }

        private static void addBounded(ArrayDeque<TimedPosition> events, TimedPosition event) {
            events.addLast(event);
            while (events.size() > MAX_EVENTS_PER_STREAM) events.removeFirst();
        }

        private static long elapsed(long tick, long previous) {
            return previous == Long.MIN_VALUE ? Long.MAX_VALUE : Math.max(0L, tick - previous);
        }
    }
}
