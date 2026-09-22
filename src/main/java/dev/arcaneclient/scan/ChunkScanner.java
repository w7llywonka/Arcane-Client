package dev.arcaneclient.scan;

import dev.arcaneclient.model.*;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Plant-only palette counting, with separate opt-in positional work for terrain ESP. */
public final class ChunkScanner {
    private static final int MAX_WORLD_OBSERVATIONS_PER_CHUNK = 192;
    public ChunkScanner() {}
    public ChunkScanner(ActivityClassifier ignored) {}
    public void resetTemporalHistory() {}
    public void forgetChunk(LevelChunk ignored) {}

    public ChunkScanJob begin(ClientLevel world, LevelChunk chunk, long tick) {
        return begin(world, chunk, tick, Integer.MIN_VALUE);
    }
    public ChunkScanJob begin(ClientLevel world, LevelChunk chunk, long tick, int observerY) {
        return begin(world, chunk, tick, observerY, false);
    }
    public ChunkScanJob begin(ClientLevel world, LevelChunk chunk, long tick, int observerY, boolean tunnels) {
        return begin(world, chunk, tick, observerY, tunnels, false);
    }
    public ChunkScanJob begin(ClientLevel world, LevelChunk chunk, long tick, int observerY, boolean tunnels, boolean ignoredLight) {
        return begin(world, chunk, tick, observerY, tunnels, true, true, false);
    }
    public ChunkScanJob begin(ClientLevel world, LevelChunk chunk, long tick, int observerY,
        boolean tunnels, boolean amethyst, boolean trails, boolean growthPoints) {
        if (chunk.getLevel() != world) throw new IllegalArgumentException("chunk does not belong to world");
        return new ChunkScanJob(world, chunk, tick, observerY, tunnels, amethyst, trails, growthPoints);
    }
    public ScanResult scan(ClientLevel world, LevelChunk chunk, long tick) {
        var job = begin(world, chunk, tick);
        while (!job.isComplete()) job.step(2048);
        return job.result();
    }

    private static BlockPosition modelPosition(BlockPos pos) {
        return new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
    }
    private static boolean tunnelPassable(String path) {
        return path.endsWith("torch") || path.endsWith("wall_torch") || path.contains("rail")
            || path.equals("redstone_wire") || path.equals("tripwire") || path.equals("ladder");
    }

    private static List<WorldObservation> collectWorldObservations(
        Map<Long, String> currentAmethyst,
        List<BlockPosition> cobbledTrailBlocks
    ) {
        ArrayList<WorldObservation> observations = new ArrayList<>();
        ArrayList<Map.Entry<Long, String>> shardPositions = new ArrayList<>();
        for (Map.Entry<Long, String> entry : currentAmethyst.entrySet()) {
            if (GrowthTransitions.amethystRank(entry.getValue()) >= 0) shardPositions.add(entry);
        }
        shardPositions.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Long, String> shard : shardPositions) {
            if (observations.size() == MAX_WORLD_OBSERVATIONS_PER_CHUNK) break;
            BlockPos position = BlockPos.of(shard.getKey());
            observations.add(new WorldObservation(
                modelPosition(position), WorldObservation.Kind.AMETHYST_SHARD, 100, false,
                GrowthTransitions.amethystRank(shard.getValue())
            ));
        }

        List<CobbledDeepslateTrailHeuristics.Trail> trails =
            CobbledDeepslateTrailHeuristics.detect(cobbledTrailBlocks, 8);
        for (CobbledDeepslateTrailHeuristics.Trail trail : trails) {
            for (BlockPosition point : trail.points()) {
                if (observations.size() == MAX_WORLD_OBSERVATIONS_PER_CHUNK) break;
                observations.add(new WorldObservation(
                    point, WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL, trail.confidence(), false
                ));
            }
        }
        observations.sort(java.util.Comparator.comparing((WorldObservation observation) -> observation.kind().ordinal())
            .thenComparingInt(observation -> observation.position().y())
            .thenComparingInt(observation -> observation.position().x())
            .thenComparingInt(observation -> observation.position().z()));
        return List.copyOf(observations);
    }

    public final class ChunkScanJob {
        private final LevelChunk chunk;
        private final long tick;
        private final int observerSectionY;
        private final boolean amethyst, trails, growthPoints;
        private final LevelChunkSection[] sections;
        private final TunnelDetector.Volume tunnelVolume;
        private final Map<Long, String> currentAmethyst = new HashMap<>();
        private final List<BlockPosition> cobbledTrailBlocks = new ArrayList<>();
        private final ScanResult.Builder builder = ScanResult.builder();
        private int sectionIndex, blockIndex, grownCount, countSections, skippedSections, inspectedBlocks, pointCount;
        private boolean sectionPrepared, positional;
        private ScanResult completed;
        private List<WorldObservation> observations = List.of();
        private List<TunnelSegment> tunnels = List.of();

        private ChunkScanJob(ClientLevel world, LevelChunk chunk, long tick, int observerY,
            boolean tunnels, boolean amethyst, boolean trails, boolean growthPoints) {
            this.chunk = chunk;
            this.tick = tick;
            this.observerSectionY = observerY;
            this.amethyst = amethyst;
            this.trails = trails;
            this.growthPoints = growthPoints;
            this.sections = chunk.getSections();
            this.tunnelVolume = tunnels ? new TunnelDetector.Volume(world.getMinY(), Math.min(51, world.getMaxY() - 1)) : null;
        }

        /** At most two palette counts per turn; optional coordinate work obeys blockBudget. */
        public int step(int blockBudget) {
            if (completed != null || blockBudget <= 0) return 0;
            int visited = 0, counted = 0;
            while (sectionIndex < sections.length) {
                LevelChunkSection section = sections[sectionIndex];
                int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
                if (!sectionPrepared) {
                    if (section.hasOnlyAir()) {
                        if (tunnelVolume != null) tunnelVolume.fillSection(baseY);
                        skippedSections++;
                        nextSection();
                        continue;
                    }
                    boolean hasGrowth = section.maybeHas(GrownBlocks::matches);
                    if (hasGrowth) {
                        if (counted >= 2) break;
                        section.getStates().count((state, count) -> {
                            if (GrownBlocks.matches(state)) grownCount += count;
                        });
                        countSections++;
                        counted++;
                    }
                    positional = tunnelVolume != null || (growthPoints && pointCount < 64 && hasGrowth)
                        || (amethyst && section.maybeHas(state -> GrowthTransitions.amethystRank(ActivityClassifier.blockPath(state)) >= 0))
                        || (trails && section.maybeHas(state -> ActivityClassifier.isCobbledDeepslateTrailPath(ActivityClassifier.blockPath(state))));
                    sectionPrepared = true;
                    if (!positional) {
                        if (!hasGrowth) skippedSections++;
                        nextSection();
                        continue;
                    }
                }
                if (visited >= blockBudget) break;
                int x = blockIndex & 15, z = (blockIndex >>> 4) & 15, y = baseY + (blockIndex >>> 8);
                inspect(section.getBlockState(x, blockIndex >>> 8, z), x, y, z);
                visited++;
                inspectedBlocks++;
                if (pointCount >= 64 && tunnelVolume == null && !amethyst && !trails) {
                    nextSection();
                    continue;
                }
                if (++blockIndex == 4096) nextSection();
            }
            if (sectionIndex == sections.length) finish();
            return visited + counted;
        }

        private void nextSection() { sectionIndex++; blockIndex = 0; sectionPrepared = false; }
        private void inspect(BlockState state, int x, int y, int z) {
            if (state.isAir()) {
                if (tunnelVolume != null) tunnelVolume.setOpen(x, y, z);
                return;
            }
            if (state.hasBlockEntity()) return;
            if (tunnelVolume == null && !amethyst && !trails && !GrownBlocks.matches(state)) return;
            String path = ActivityClassifier.blockPath(state);
            if (tunnelVolume != null && tunnelPassable(path)) tunnelVolume.setOpen(x, y, z);
            boolean plantPoint = growthPoints && pointCount < 64 && GrownBlocks.matches(state);
            boolean shard = amethyst && currentAmethyst.size() < MAX_WORLD_OBSERVATIONS_PER_CHUNK && GrowthTransitions.amethystRank(path) >= 0;
            boolean trail = trails && cobbledTrailBlocks.size() < 256 && ActivityClassifier.isCobbledDeepslateTrailPath(path);
            if (!plantPoint && !shard && !trail) return;
            BlockPos position = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
            if (plantPoint) {
                builder.addStatic(SignalCategory.GROWN_PLANTS, EvidenceFamily.GROWTH, modelPosition(position), "grown " + path, 1);
                pointCount++;
            }
            if (shard) currentAmethyst.put(position.asLong(), path);
            if (trail) cobbledTrailBlocks.add(modelPosition(position));
        }
        private void finish() {
            observations = collectWorldObservations(currentAmethyst, cobbledTrailBlocks);
            if (tunnelVolume != null) tunnels = TunnelDetector.detect(tunnelVolume, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
            completed = builder.completeSnapshot(tick, sections.length, sections.length).build();
        }
        public boolean isComplete() { return completed != null; }
        private void requireComplete() { if (!isComplete()) throw new IllegalStateException("scan incomplete"); }
        public ScanResult result() { requireComplete(); return completed; }
        public int observerSectionY() { return observerSectionY; }
        public int grownCount() { requireComplete(); return grownCount; }
        public int countedSections() { return countSections; }
        public int skippedSections() { return skippedSections; }
        public int inspectedBlocks() { return inspectedBlocks; }
        public List<TunnelSegment> tunnels() { requireComplete(); return tunnels; }
        public List<WorldObservation> worldObservations() { requireComplete(); return observations; }
        public List<BlockPosition> cobbledTrailCandidates() { requireComplete(); return List.copyOf(cobbledTrailBlocks); }
    }
}
