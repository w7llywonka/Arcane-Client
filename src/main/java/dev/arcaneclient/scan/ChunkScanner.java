package dev.arcaneclient.scan;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.model.TunnelSegment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

@Environment(EnvType.CLIENT)
public final class ChunkScanner {
    private static final int TEMPORAL_DECAY_TICKS = 3_600;
    private static final int MAX_TEMPORAL_SNAPSHOTS = 2_048;
    private static final long HASH_SEED = -3_750_763_034_362_895_579L;

    private final ActivityClassifier classifier;
    private final LinkedHashMap<Long, GrowthSnapshot> temporalSnapshots =
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, GrowthSnapshot> eldest) {
                return this.size() > MAX_TEMPORAL_SNAPSHOTS;
            }
        };

    public ChunkScanner() {
        this(new ActivityClassifier());
    }

    ChunkScanner(ActivityClassifier classifier) {
        this.classifier = classifier;
    }

    public ChunkScanJob begin(ClientWorld world, WorldChunk chunk, long tick) {
        return this.begin(world, chunk, tick, Integer.MIN_VALUE);
    }

    public ChunkScanJob begin(ClientWorld world, WorldChunk chunk, long tick, int observerSectionY) {
        return this.begin(world, chunk, tick, observerSectionY, false);
    }

    public ChunkScanJob begin(
        ClientWorld world,
        WorldChunk chunk,
        long tick,
        int observerSectionY,
        boolean collectTunnels
    ) {
        if (chunk.getWorld() != world) {
            throw new IllegalArgumentException("chunk does not belong to the supplied client world");
        }
        return new ChunkScanJob(world, chunk, tick, observerSectionY, collectTunnels);
    }

    public void resetTemporalHistory() {
        this.temporalSnapshots.clear();
    }

    public ScanResult scan(ClientWorld world, WorldChunk chunk, long tick) {
        ChunkScanJob job = this.begin(world, chunk, tick);
        while (!job.isComplete()) {
            job.step(65_536);
        }
        return job.result();
    }

    private static boolean tunnelPassable(String path) {
        return path.endsWith("torch")
            || path.endsWith("wall_torch")
            || path.contains("rail")
            || path.equals("redstone_wire")
            || path.equals("tripwire")
            || path.equals("ladder");
    }

    private static void emitFieldEvidence(
        ScanResult.Builder result,
        Map<Integer, GrowthGrid> cropLayers,
        Map<Integer, GrowthGrid> farmlandLayers
    ) {
        GrowthGrid organizedRepresentative = null;
        int organizedStrength = 0;
        int organizedBlocks = 0;
        int organizedLayers = 0;

        GrowthGrid synchronizedRepresentative = null;
        int synchronizedStrength = 0;
        int synchronizedBlocks = 0;

        GrowthGrid matureRepresentative = null;
        int matureStrength = 0;
        int matureBlocks = 0;

        for (Map.Entry<Integer, GrowthGrid> entry : cropLayers.entrySet()) {
            GrowthGrid crops = entry.getValue();
            GrowthGrid farmland = farmlandLayers.get(entry.getKey() - 1);
            int support = farmland == null ? 0 : crops.overlap(farmland);
            int maxRun = crops.maxRun();

            int organized = EvidenceHeuristics.organizedField(
                crops.count,
                support,
                maxRun,
                crops.isRectangular(),
                crops.fillPercent()
            );
            if (organized > 0) {
                organizedBlocks += crops.count;
                organizedLayers++;
                int layeredStrength = organized + Math.min(36, (organizedLayers - 1) * 12);
                if (layeredStrength > organizedStrength) {
                    organizedStrength = layeredStrength;
                    organizedRepresentative = crops;
                }
            }

            int synchronizedGrowth = EvidenceHeuristics.synchronizedCropGrowth(
                crops.count,
                crops.stagedCount,
                crops.dominantGrowthCount(),
                maxRun
            );
            if (synchronizedGrowth > synchronizedStrength) {
                synchronizedStrength = synchronizedGrowth;
                synchronizedRepresentative = crops;
                synchronizedBlocks = crops.stagedCount;
            }

            int mature = EvidenceHeuristics.matureCropConcentration(crops.count, crops.matureCount, maxRun);
            if (mature > matureStrength) {
                matureStrength = mature;
                matureRepresentative = crops;
                matureBlocks = crops.matureCount;
            }
        }

        if (organizedRepresentative != null) {
            String reason = organizedLayers > 1
                ? "organized crop layers x" + organizedLayers + " / crops x" + organizedBlocks
                : "organized crop plot x" + organizedBlocks;
            result.addStatic(
                SignalCategory.CULTIVATION,
                modelPosition(organizedRepresentative.representative),
                reason,
                Math.min(190, organizedStrength)
            );
        }
        if (synchronizedRepresentative != null) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                modelPosition(synchronizedRepresentative.representative),
                "synchronized crop stages x" + synchronizedBlocks,
                synchronizedStrength
            );
        }
        if (matureRepresentative != null) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                modelPosition(matureRepresentative.representative),
                "mature crop concentration x" + matureBlocks,
                matureStrength
            );
        }
    }

    private static void emitSaplingEvidence(ScanResult.Builder result, Map<Integer, GrowthGrid> layers) {
        GrowthGrid representative = null;
        int bestStrength = 0;
        int total = 0;
        for (GrowthGrid grid : layers.values()) {
            int strength = EvidenceHeuristics.organizedSaplings(grid.count, grid.maxRun(), grid.isRectangular());
            if (strength <= 0) {
                continue;
            }
            total += grid.count;
            if (strength > bestStrength) {
                bestStrength = strength;
                representative = grid;
            }
        }
        if (representative != null) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                modelPosition(representative.representative),
                "organized sapling layout x" + total,
                Math.min(130, bestStrength)
            );
        }
    }

    private static void emitVerticalEvidence(
        ScanResult.Builder result,
        Map<String, VerticalGrid> verticalGrowth
    ) {
        String bestPath = null;
        VerticalGrid best = null;
        int bestStrength = 0;
        for (Map.Entry<String, VerticalGrid> entry : verticalGrowth.entrySet()) {
            VerticalGrid grid = entry.getValue();
            int strength = EvidenceHeuristics.verticalGrowthColumns(
                grid.totalBlocks,
                grid.columns,
                grid.maximumHeight(),
                grid.maxRun(),
                grid.isRectangular(),
                grid.fillPercent()
            );
            if (strength <= bestStrength) {
                continue;
            }
            bestPath = entry.getKey();
            best = grid;
            bestStrength = strength;
        }
        if (best != null) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                modelPosition(best.representative),
                "organized " + bestPath.replace('_', ' ') + " columns x" + best.columns,
                bestStrength
            );
        }
    }

    private static void emitImportedGrowth(
        ScanResult.Builder result,
        Map<String, ImportedGrowth> importedGrowth
    ) {
        int count = 0;
        int types = 0;
        BlockPos representative = null;
        for (ImportedGrowth imported : importedGrowth.values()) {
            count += imported.count;
            types++;
            if (representative == null) {
                representative = imported.representative;
            }
        }
        int strength = EvidenceHeuristics.importedGrowthCluster(count);
        if (strength > 0) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                modelPosition(representative),
                "growth outside native biome x" + count + " / types x" + types,
                strength
            );
        }
    }

    private void emitTemporalGrowth(
        ScanResult.Builder result,
        WorldChunk chunk,
        long[] hashes,
        int[] counts,
        int[] stageTotals,
        int[] matureCounts,
        int observerSectionY,
        long tick
    ) {
        GrowthSnapshot current = new GrowthSnapshot(
            hashes.clone(),
            counts.clone(),
            stageTotals.clone(),
            matureCounts.clone(),
            observerSectionY
        );
        GrowthSnapshot previous = this.temporalSnapshots.put(chunk.getPos().toLong(), current);
        if (previous == null
            || previous.hashes.length != hashes.length
            || previous.observerSectionY != observerSectionY) {
            return;
        }

        int changedSections = 0;
        int stageIncrease = 0;
        int stageDecrease = 0;
        int matureDecrease = 0;
        int countIncrease = 0;
        int countDecrease = 0;
        int firstChangedSection = -1;

        for (int index = 0; index < hashes.length; index++) {
            if (previous.hashes[index] == hashes[index]
                && previous.counts[index] == counts[index]
                && previous.stageTotals[index] == stageTotals[index]
                && previous.matureCounts[index] == matureCounts[index]) {
                continue;
            }
            changedSections++;
            if (firstChangedSection < 0) {
                firstChangedSection = index;
            }

            int stageDelta = stageTotals[index] - previous.stageTotals[index];
            stageIncrease += Math.max(0, stageDelta);
            stageDecrease += Math.max(0, -stageDelta);

            int countDelta = counts[index] - previous.counts[index];
            countIncrease += Math.max(0, countDelta);
            countDecrease += Math.max(0, -countDelta);
            matureDecrease += Math.max(0, previous.matureCounts[index] - matureCounts[index]);
        }

        if (firstChangedSection < 0) {
            return;
        }

        int y = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(firstChangedSection)) + 8;
        BlockPosition position = new BlockPosition(chunk.getPos().getCenterX(), y, chunk.getPos().getCenterZ());

        int growthStrength = EvidenceHeuristics.observedGrowth(changedSections, stageIncrease, countIncrease);
        if (growthStrength > 0) {
            result.addLive(
                SignalCategory.NATURAL_GROWTH,
                position,
                "observed growth between scans x" + (stageIncrease + countIncrease),
                growthStrength,
                tick,
                TEMPORAL_DECAY_TICKS
            );
        }

        int harvestStrength = EvidenceHeuristics.observedHarvest(
            changedSections,
            stageDecrease,
            matureDecrease,
            countDecrease
        );
        if (harvestStrength > 0) {
            result.addLive(
                SignalCategory.CULTIVATION,
                position,
                "observed crop harvest or reset x" + (stageDecrease + countDecrease),
                harvestStrength,
                tick,
                TEMPORAL_DECAY_TICKS
            );
        }
    }

    private static BlockPosition modelPosition(BlockPos position) {
        return new BlockPosition(position.getX(), position.getY(), position.getZ());
    }

    private static long mixHash(long hash, int position, int stateHash) {
        long value = (long) position << 32 ^ (long) stateHash & 0xFFFF_FFFFL;
        hash ^= value + -7_046_029_254_386_353_131L + (hash << 6) + (hash >>> 2);
        return hash * 1_099_511_628_211L;
    }

    @Environment(EnvType.CLIENT)
    public final class ChunkScanJob {
        private final ClientWorld world;
        private final WorldChunk chunk;
        private final long tick;
        private final int observerSectionY;
        private final ScanResult.Builder builder = ScanResult.builder();
        private final Map<Integer, GrowthGrid> cropLayers = new HashMap<>();
        private final Map<Integer, GrowthGrid> saplingLayers = new HashMap<>();
        private final Map<Integer, GrowthGrid> farmlandLayers = new HashMap<>();
        private final Map<String, VerticalGrid> verticalGrowth = new HashMap<>();
        private final Map<String, ImportedGrowth> importedGrowth = new HashMap<>();
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private final ChunkSection[] sections;
        private final int minX;
        private final int minZ;
        private final long[] growthHashes;
        private final int[] growthCounts;
        private final int[] growthStageTotals;
        private final int[] matureCounts;
        private final TunnelDetector.Volume tunnelVolume;

        private int sectionIndex;
        private int blockIndex;
        private ScanResult completed;
        private List<TunnelSegment> tunnels = List.of();

        private ChunkScanJob(
            ClientWorld world,
            WorldChunk chunk,
            long tick,
            int observerSectionY,
            boolean collectTunnels
        ) {
            this.world = world;
            this.chunk = chunk;
            this.tick = tick;
            this.observerSectionY = observerSectionY;
            this.sections = chunk.getSectionArray();
            this.minX = chunk.getPos().getStartX();
            this.minZ = chunk.getPos().getStartZ();
            this.growthHashes = new long[this.sections.length];
            this.growthCounts = new int[this.sections.length];
            this.growthStageTotals = new int[this.sections.length];
            this.matureCounts = new int[this.sections.length];
            this.tunnelVolume = collectTunnels
                ? new TunnelDetector.Volume(world.getBottomY(), Math.min(51, world.getTopYInclusive() - 1))
                : null;
            Arrays.fill(this.growthHashes, HASH_SEED);
        }

        public int step(int blockBudget) {
            if (this.completed != null || blockBudget <= 0) {
                return 0;
            }

            int visited = 0;
            while (this.sectionIndex < this.sections.length && visited < blockBudget) {
                ChunkSection section = this.sections[this.sectionIndex];
                if (section.isEmpty()) {
                    if (this.tunnelVolume != null) {
                        this.tunnelVolume.fillSection(
                            ChunkSectionPos.getBlockCoord(this.chunk.sectionIndexToCoord(this.sectionIndex))
                        );
                    }
                    this.advanceSection();
                    continue;
                }
                if (this.tunnelVolume == null && !section.hasAny(ChunkScanner.this.classifier::isRelevant)) {
                    this.advanceSection();
                    continue;
                }

                int localX = this.blockIndex & 0xF;
                int localZ = this.blockIndex >>> 4 & 0xF;
                int localY = this.blockIndex >>> 8 & 0xF;
                int y = ChunkSectionPos.getBlockCoord(this.chunk.sectionIndexToCoord(this.sectionIndex)) + localY;
                this.cursor.set(this.minX + localX, y, this.minZ + localZ);
                this.inspect(section.getBlockState(localX, localY, localZ), localX, localZ, y);

                visited++;
                this.blockIndex++;
                if (this.blockIndex == 4_096) {
                    this.advanceSection();
                }
            }

            if (this.sectionIndex == this.sections.length) {
                this.finish();
            }
            return visited;
        }

        private void advanceSection() {
            this.sectionIndex++;
            this.blockIndex = 0;
        }

        public boolean isComplete() {
            return this.completed != null;
        }

        public ScanResult result() {
            if (this.completed == null) {
                throw new IllegalStateException("scan is not complete");
            }
            return this.completed;
        }

        public int observerSectionY() {
            return this.observerSectionY;
        }

        public List<TunnelSegment> tunnels() {
            if (this.completed == null) {
                throw new IllegalStateException("scan is not complete");
            }
            return this.tunnels;
        }

        private void inspect(BlockState state, int localX, int localZ, int y) {
            if (state.isAir()) {
                if (this.tunnelVolume != null) {
                    this.tunnelVolume.setOpen(localX, y, localZ);
                }
                return;
            }

            ActivityClassifier.BlockFacts facts = ChunkScanner.this.classifier.facts(state);
            if (this.tunnelVolume != null && tunnelPassable(facts.path())) {
                this.tunnelVolume.setOpen(localX, y, localZ);
            }
            if (!facts.isGrowth() && !facts.farmland()) {
                return;
            }

            if (facts.farmland()) {
                this.farmlandLayers
                    .computeIfAbsent(y, ignored -> new GrowthGrid())
                    .add(localX, localZ, this.cursor, -1, false);
                return;
            }

            int stageBucket = facts.growthBucket();
            int stateHash = facts.path().hashCode() * 31 + stageBucket + 1;
            this.growthHashes[this.sectionIndex] = mixHash(
                this.growthHashes[this.sectionIndex],
                this.blockIndex,
                stateHash
            );
            this.growthCounts[this.sectionIndex]++;
            if (stageBucket >= 0) {
                this.growthStageTotals[this.sectionIndex] += stageBucket;
            }
            if (facts.mature()) {
                this.matureCounts[this.sectionIndex]++;
            }

            switch (facts.kind()) {
                case FIELD -> this.cropLayers
                    .computeIfAbsent(y, ignored -> new GrowthGrid())
                    .add(localX, localZ, this.cursor, stageBucket, facts.mature());
                case SAPLING -> this.saplingLayers
                    .computeIfAbsent(y, ignored -> new GrowthGrid())
                    .add(localX, localZ, this.cursor, stageBucket, facts.mature());
                case VERTICAL -> {
                    if (facts.path().equals("sugar_cane") || facts.path().equals("cactus")) {
                        this.verticalGrowth
                            .computeIfAbsent(facts.path(), ignored -> new VerticalGrid())
                            .add(localX, localZ, this.cursor);
                    }
                }
                case NONE -> {
                }
            }

            if (facts.importedPlantCandidate()
                && ChunkScanner.this.classifier.isImportedPlant(this.world, this.cursor, facts.path())) {
                this.importedGrowth
                    .computeIfAbsent(facts.path(), ignored -> new ImportedGrowth(this.cursor.toImmutable()))
                    .count++;
            }
        }

        private void finish() {
            emitFieldEvidence(this.builder, this.cropLayers, this.farmlandLayers);
            emitSaplingEvidence(this.builder, this.saplingLayers);
            emitVerticalEvidence(this.builder, this.verticalGrowth);
            emitImportedGrowth(this.builder, this.importedGrowth);
            ChunkScanner.this.emitTemporalGrowth(
                this.builder,
                this.chunk,
                this.growthHashes,
                this.growthCounts,
                this.growthStageTotals,
                this.matureCounts,
                this.observerSectionY,
                this.tick
            );
            if (this.tunnelVolume != null) {
                this.tunnels = TunnelDetector.detect(this.tunnelVolume, this.minX, this.minZ);
            }
            this.completed = this.builder.build();
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class GrowthGrid {
        private final boolean[] occupied = new boolean[256];
        private final int[] growthBuckets = new int[5];
        private int count;
        private int stagedCount;
        private int matureCount;
        private int minX = 16;
        private int maxX = -1;
        private int minZ = 16;
        private int maxZ = -1;
        private BlockPos representative;

        private void add(int x, int z, BlockPos position, int growthBucket, boolean mature) {
            int index = z * 16 + x;
            if (this.occupied[index]) {
                return;
            }
            this.occupied[index] = true;
            this.count++;
            this.minX = Math.min(this.minX, x);
            this.maxX = Math.max(this.maxX, x);
            this.minZ = Math.min(this.minZ, z);
            this.maxZ = Math.max(this.maxZ, z);
            if (this.representative == null) {
                this.representative = position.toImmutable();
            }
            if (growthBucket >= 0 && growthBucket < this.growthBuckets.length) {
                this.growthBuckets[growthBucket]++;
                this.stagedCount++;
            }
            if (mature) {
                this.matureCount++;
            }
        }

        private int overlap(GrowthGrid other) {
            int overlap = 0;
            for (int index = 0; index < this.occupied.length; index++) {
                if (this.occupied[index] && other.occupied[index]) {
                    overlap++;
                }
            }
            return overlap;
        }

        private int maxRun() {
            int best = 0;
            for (int fixed = 0; fixed < 16; fixed++) {
                int row = 0;
                int column = 0;
                for (int moving = 0; moving < 16; moving++) {
                    row = this.occupied[fixed * 16 + moving] ? row + 1 : 0;
                    column = this.occupied[moving * 16 + fixed] ? column + 1 : 0;
                    best = Math.max(best, Math.max(row, column));
                }
            }
            return best;
        }

        private int dominantGrowthCount() {
            int best = 0;
            for (int countAtStage : this.growthBuckets) {
                best = Math.max(best, countAtStage);
            }
            return best;
        }

        private boolean isRectangular() {
            if (this.count < 6 || this.maxX - this.minX < 1 || this.maxZ - this.minZ < 1) {
                return false;
            }
            return this.fillPercent() >= 60;
        }

        private int fillPercent() {
            if (this.count == 0) {
                return 0;
            }
            int area = (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1);
            return area <= 0 ? 0 : this.count * 100 / area;
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class VerticalGrid {
        private final int[] heights = new int[256];
        private int totalBlocks;
        private int columns;
        private int minX = 16;
        private int maxX = -1;
        private int minZ = 16;
        private int maxZ = -1;
        private BlockPos representative;

        private void add(int x, int z, BlockPos position) {
            int index = z * 16 + x;
            if (this.heights[index] == 0) {
                this.columns++;
                this.minX = Math.min(this.minX, x);
                this.maxX = Math.max(this.maxX, x);
                this.minZ = Math.min(this.minZ, z);
                this.maxZ = Math.max(this.maxZ, z);
            }
            this.heights[index]++;
            this.totalBlocks++;
            if (this.representative == null) {
                this.representative = position.toImmutable();
            }
        }

        private int maximumHeight() {
            int best = 0;
            for (int height : this.heights) {
                best = Math.max(best, height);
            }
            return best;
        }

        private int maxRun() {
            int best = 0;
            for (int fixed = 0; fixed < 16; fixed++) {
                int row = 0;
                int column = 0;
                for (int moving = 0; moving < 16; moving++) {
                    row = this.heights[fixed * 16 + moving] > 0 ? row + 1 : 0;
                    column = this.heights[moving * 16 + fixed] > 0 ? column + 1 : 0;
                    best = Math.max(best, Math.max(row, column));
                }
            }
            return best;
        }

        private boolean isRectangular() {
            return this.columns >= 6
                && this.maxX - this.minX >= 1
                && this.maxZ - this.minZ >= 1
                && this.fillPercent() >= 65;
        }

        private int fillPercent() {
            if (this.columns == 0) {
                return 0;
            }
            int area = (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1);
            return area <= 0 ? 0 : this.columns * 100 / area;
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class ImportedGrowth {
        private final BlockPos representative;
        private int count;

        private ImportedGrowth(BlockPos representative) {
            this.representative = representative;
        }
    }

    @Environment(EnvType.CLIENT)
    private record GrowthSnapshot(
        long[] hashes,
        int[] counts,
        int[] stageTotals,
        int[] matureCounts,
        int observerSectionY
    ) {
    }
}
