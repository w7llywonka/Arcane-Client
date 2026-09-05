package dev.arcaneclient.scan;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.CobbledDeepslateTrailHeuristics;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.model.TunnelSegment;
import dev.arcaneclient.model.WorldObservation;
import dev.arcaneclient.scan.ActivityClassifier;
import dev.arcaneclient.scan.EvidenceHeuristics;
import dev.arcaneclient.scan.TransientEntityFilter;
import dev.arcaneclient.scan.TunnelDetector;
import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.entity.passive.HappyGhastEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

@Environment(value=EnvType.CLIENT)
public final class ChunkScanner {
    private static final int TEMPORAL_DECAY_TICKS = 12000;
    private static final int MAX_TEMPORAL_SNAPSHOTS = 2048;
    private static final int MAX_WORLD_OBSERVATIONS_PER_CHUNK = 192;
    private static final long HASH_SEED = -3750763034362895579L;
    private static final Set<String> PENNED_ANIMALS = Set.of("cow", "sheep", "pig", "chicken", "rabbit", "goat", "llama", "horse", "donkey", "mule");
    private final ActivityClassifier classifier;
    private final LinkedHashMap<Long, ChunkSnapshot> temporalSnapshots = new LinkedHashMap<Long, ChunkSnapshot>(256, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ChunkSnapshot> eldest) {
            return this.size() > 2048;
        }
    };

    public ChunkScanner() {
        this(new ActivityClassifier());
    }

    public ChunkScanner(ActivityClassifier classifier) {
        this.classifier = classifier;
    }

    public ChunkScanJob begin(ClientWorld level, WorldChunk chunk, long tick) {
        return this.begin(level, chunk, tick, Integer.MIN_VALUE);
    }

    public ChunkScanJob begin(ClientWorld level, WorldChunk chunk, long tick, int observerSectionY) {
        return this.begin(level, chunk, tick, observerSectionY, false);
    }

    public ChunkScanJob begin(ClientWorld level, WorldChunk chunk, long tick, int observerSectionY, boolean collectTunnels) {
        return this.begin(level, chunk, tick, observerSectionY, collectTunnels, true);
    }

    public ChunkScanJob begin(
        ClientWorld level,
        WorldChunk chunk,
        long tick,
        int observerSectionY,
        boolean collectTunnels,
        boolean collectConcealedLight
    ) {
        if (chunk.getWorld() != level) {
            throw new IllegalArgumentException("chunk does not belong to the supplied client level");
        }
        return new ChunkScanJob(level, chunk, tick, observerSectionY, collectTunnels, collectConcealedLight);
    }

    public void resetTemporalHistory() {
        this.temporalSnapshots.clear();
    }

    public void forgetChunk(WorldChunk chunk) {
        this.temporalSnapshots.remove(chunk.getPos().toLong());
    }

    public ScanResult scan(ClientWorld level, WorldChunk chunk, long tick) {
        ChunkScanJob job = this.begin(level, chunk, tick);
        while (!job.isComplete()) {
            job.step(65536);
        }
        return job.result();
    }

    private static boolean tunnelPassable(String path) {
        return path.endsWith("torch") || path.endsWith("wall_torch") || path.contains("rail") || path.equals("redstone_wire") || path.equals("tripwire") || path.equals("ladder");
    }

    private static void emitBlockAggregates(ScanResult.Builder result, EnumMap<ActivityClassifier.Signal, Aggregate> aggregates, boolean generatedDeepStructure) {
        for (Map.Entry<ActivityClassifier.Signal, Aggregate> entry : aggregates.entrySet()) {
            ActivityClassifier.Signal signal = entry.getKey();
            if (signal == ActivityClassifier.Signal.AMETHYST_STAGE) continue;
            if (generatedDeepStructure && signal == ActivityClassifier.Signal.WORKED_DEEPSLATE) continue;
            Aggregate aggregate = entry.getValue();
            result.addStatic(signal.category(), ChunkScanner.modelPosition(aggregate.representative), signal.reason() + " x" + aggregate.count, signal.aggregateStrength(aggregate.count));
        }
    }

    private static void emitFarmEvidence(ScanResult.Builder result, Map<Integer, Grid> cropLayers, Map<Integer, Grid> farmlandLayers) {
        Grid farmland;
        int growthStrength;
        Grid crop = ChunkScanner.strongestGrid(cropLayers);
        if (crop != null && crop.count >= 4 && crop.maxRun() >= 4) {
            result.addStatic(SignalCategory.CULTIVATION, ChunkScanner.modelPosition(crop.representative), "aligned crop row x" + crop.count, Math.min(130, 38 + crop.count * 5));
        }
        if (crop != null && crop.isRectangular()) {
            result.addStatic(SignalCategory.CULTIVATION, ChunkScanner.modelPosition(crop.representative), "rectangular crop plot x" + crop.count, Math.min(130, 45 + crop.count * 4));
        }
        if (crop != null && (growthStrength = EvidenceHeuristics.synchronizedCropGrowth(crop.count, crop.stagedCount, crop.dominantGrowthCount(), crop.maxRun())) > 0) {
            result.addStatic(SignalCategory.CULTIVATION, ChunkScanner.modelPosition(crop.representative), "synchronized crop growth x" + crop.stagedCount, growthStrength);
        }
        if ((farmland = ChunkScanner.strongestGrid(farmlandLayers)) != null && farmland.count >= 4 && farmland.maxRun() >= 4) {
            result.addStatic(SignalCategory.CULTIVATION, ChunkScanner.modelPosition(farmland.representative), "aligned farmland x" + farmland.count, Math.min(100, 30 + farmland.count * 4));
        }
    }

    private static void emitGrowthGeometry(
        ScanResult.Builder result,
        ClientWorld level,
        ActivityClassifier classifier,
        Map<Integer, Grid> berryLayers,
        KelpColumns kelp
    ) {
        Grid berries = ChunkScanner.strongestGrid(berryLayers);
        if (berries != null) {
            boolean nativeTaiga = !classifier.isImportedPlant(level, berries.representative, "sweet_berry_bush");
            int strength = EvidenceHeuristics.cultivatedBerryPatch(berries.count, berries.maxRun(), berries.isRectangular(), nativeTaiga);
            if (strength > 0) {
                result.addStatic(
                    SignalCategory.CULTIVATION,
                    ChunkScanner.modelPosition(berries.representative),
                    "cultivated sweet-berry rows x" + berries.count,
                    strength
                );
            }
        }
        int kelpStrength = EvidenceHeuristics.cultivatedKelpColumns(kelp.longColumns(), kelp.alignedLongColumns(level.getSeaLevel() - 1));
        if (kelpStrength > 0) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                ChunkScanner.modelPosition(kelp.representative),
                "aligned long kelp farm columns x" + kelp.longColumns(),
                kelpStrength
            );
        }
    }

    private static Grid strongestGrid(Map<Integer, Grid> layers) {
        Grid best = null;
        for (Grid grid : layers.values()) {
            if (best != null && grid.count <= best.count) continue;
            best = grid;
        }
        return best;
    }

    private static void emitDripstoneFarm(ScanResult.Builder result, List<BlockPos> dripstone, List<BlockPos> cauldrons) {
        int pairs = 0;
        BlockPos representative = null;
        block0: for (BlockPos cauldron : cauldrons) {
            for (BlockPos point : dripstone) {
                int verticalDistance = point.getY() - cauldron.getY();
                if (verticalDistance <= 0 || verticalDistance > 16 || Math.abs(point.getX() - cauldron.getX()) > 1 || Math.abs(point.getZ() - cauldron.getZ()) > 1) continue;
                ++pairs;
                if (representative != null) continue block0;
                representative = cauldron;
                continue block0;
            }
        }
        if (pairs > 0) {
            result.addStatic(SignalCategory.INFRASTRUCTURE, ChunkScanner.modelPosition(representative), "dripstone above cauldrons x" + pairs, Math.min(150, 70 + pairs * 12));
        }
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
            BlockPos position = BlockPos.fromLong(shard.getKey());
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

    private void emitTemporalChanges(ScanResult.Builder result, WorldChunk chunk, long[] hashes, int[] counts, int observerSectionY, long tick) {
        ChunkSnapshot current = new ChunkSnapshot((long[])hashes.clone(), (int[])counts.clone(), observerSectionY);
        ChunkSnapshot previous = this.temporalSnapshots.put(chunk.getPos().toLong(), current);
        if (previous == null || previous.hashes.length != hashes.length || previous.observerSectionY != observerSectionY) {
            return;
        }
        int changedSections = 0;
        int totalCountDelta = 0;
        int firstChangedSection = -1;
        for (int index = 0; index < hashes.length; ++index) {
            if (previous.hashes[index] == hashes[index] && previous.counts[index] == counts[index]) continue;
            ++changedSections;
            totalCountDelta += Math.abs(previous.counts[index] - counts[index]);
            if (firstChangedSection >= 0) continue;
            firstChangedSection = index;
        }
        int strength = EvidenceHeuristics.stableLayoutChange(changedSections, totalCountDelta);
        if (strength > 0) {
            int y = ChunkSectionPos.getBlockCoord((int)chunk.sectionIndexToCoord(firstChangedSection)) + 8;
            BlockPos position = new BlockPos(chunk.getPos().getCenterX(), y, chunk.getPos().getCenterZ());
            result.addLive(SignalCategory.LIVE_ACTIVITY, ChunkScanner.modelPosition(position), "stable block layout changed between scans", strength, tick, 12000);
        }
    }

    private void emitBlockEntities(ScanResult.Builder result, WorldChunk chunk) {
        TreeSet<String> highTypes = new TreeSet<String>();
        TreeSet<String> functionalTypes = new TreeSet<String>();
        BlockPos highPosition = null;
        BlockPos functionalPosition = null;
        int highCount = 0;
        int functionalCount = 0;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            String type = ActivityClassifier.blockEntityTypePath(blockEntity);
            if (ScannerStorageFilter.isStoragePath(type)) continue;
            if (this.classifier.isHighBlockEntityType(type)) {
                highTypes.add(type);
                ++highCount;
                if (highPosition != null) continue;
                highPosition = blockEntity.getPos();
                continue;
            }
            if (!this.classifier.isFunctionalBlockEntityType(type)) continue;
            functionalTypes.add(type);
            ++functionalCount;
            if (functionalPosition != null) continue;
            functionalPosition = blockEntity.getPos();
        }
        if (highCount > 0) {
            result.addStatic(SignalCategory.INFRASTRUCTURE, ChunkScanner.modelPosition(highPosition), "high-signal block entity types: " + ChunkScanner.summarize(highTypes), Math.min(200, 110 + highCount * 12));
        }
        if (functionalCount >= 2) {
            result.addStatic(SignalCategory.INFRASTRUCTURE, ChunkScanner.modelPosition(functionalPosition), "functional block entity types: " + ChunkScanner.summarize(functionalTypes), Math.min(130, 25 + functionalCount * 8));
        }
    }

    private static void emitEntities(ScanResult.Builder result, ClientWorld level, WorldChunk chunk, long tick) {
        int animalStrength;
        int villagerStrength;
        int experienceStrength;
        double minX = chunk.getPos().getStartX();
        double minZ = chunk.getPos().getStartZ();
        Box bounds = new Box(minX, (double)chunk.getBottomY(), minZ, minX + 16.0, (double)(chunk.getTopYInclusive() + 1), minZ + 16.0);
        List<Entity> entities = level.getOtherEntities((Entity)null, bounds, entity -> true);
        HashMap<String, Integer> animalTypes = new HashMap<String, Integer>();
        EntitySignals signals = new EntitySignals();
        int droppedItemEntities = 0;
        int droppedItemCount = 0;
        int experienceOrbs = 0;
        int experienceValue = 0;
        int tradedVillagers = 0;
        int tradedVillagerLevels = 0;
        BlockPos itemPosition = null;
        BlockPos experiencePosition = null;
        BlockPos villagerPosition = null;
        BlockPos animalPosition = null;
        ClientPlayerEntity localPlayer = level.getPlayers().stream().filter(ClientPlayerEntity.class::isInstance).map(ClientPlayerEntity.class::cast).findFirst().orElse(null);
        for (Entity entity2 : entities) {
            VillagerEntity villager;
            ItemEntity item;
            boolean remoteTransient;
            Leashable leashable;
            LivingEntity living;
            TameableEntity tamable;
            String type = ActivityClassifier.entityTypePath(entity2);
            if (PENNED_ANIMALS.contains(type)) {
                animalTypes.merge(type, 1, Integer::sum);
                if (animalPosition == null) {
                    animalPosition = entity2.getBlockPos();
                }
            }
            if (entity2 instanceof TameableEntity && (tamable = (TameableEntity)entity2).isTamed()) {
                signals.addTamed(entity2);
            }
            if (entity2 instanceof CopperGolemEntity || entity2 instanceof HappyGhastEntity) {
                signals.addCreated(entity2);
            }
            if (!(!(entity2 instanceof LivingEntity) || (living = (LivingEntity)entity2).getEquippedStack(EquipmentSlot.SADDLE).isEmpty() && living.getEquippedStack(EquipmentSlot.BODY).isEmpty())) {
                signals.addEquipped(entity2);
            }
            if (entity2 instanceof Leashable && (leashable = (Leashable)entity2).isLeashed()) {
                signals.addLeashed(entity2);
            }
            if (entity2 instanceof ArmorStandEntity || entity2 instanceof ItemFrameEntity || entity2 instanceof PaintingEntity) {
                signals.addDecorative(entity2);
            }
            if (entity2 instanceof AbstractMinecartEntity || entity2 instanceof AbstractBoatEntity) {
                signals.addVehicle(entity2);
            }
            if (entity2.hasCustomName()) {
                signals.addNamed(entity2);
            }
            boolean bl = remoteTransient = localPlayer == null || TransientEntityFilter.remoteEnoughFromPlayer(entity2.squaredDistanceTo((Entity)localPlayer));
            if (remoteTransient && entity2 instanceof ItemEntity && !(item = (ItemEntity)entity2).getStack().isEmpty()) {
                ++droppedItemEntities;
                droppedItemCount += item.getStack().getCount();
                BlockPos blockPos = itemPosition = itemPosition == null ? item.getBlockPos() : itemPosition;
            }
            if (remoteTransient && entity2 instanceof ExperienceOrbEntity) {
                ExperienceOrbEntity orb = (ExperienceOrbEntity)entity2;
                ++experienceOrbs;
                experienceValue += orb.getValue();
                BlockPos blockPos = experiencePosition = experiencePosition == null ? orb.getBlockPos() : experiencePosition;
            }
            if (!(entity2 instanceof VillagerEntity) || (villager = (VillagerEntity)entity2).getVillagerData().level() < 2) continue;
            ++tradedVillagers;
            tradedVillagerLevels += villager.getVillagerData().level();
            villagerPosition = villagerPosition == null ? villager.getBlockPos() : villagerPosition;
        }
        signals.emit(result);
        int droppedItemStrength = EvidenceHeuristics.droppedItemCluster(droppedItemEntities, droppedItemCount);
        if (droppedItemStrength > 0) {
            result.addLive(SignalCategory.LIVE_ACTIVITY, ChunkScanner.modelPosition(itemPosition), "active dropped-item collection cluster", droppedItemStrength, tick, 2400);
        }
        if ((experienceStrength = EvidenceHeuristics.experienceCluster(experienceOrbs, experienceValue)) > 0) {
            result.addLive(SignalCategory.LIVE_ACTIVITY, ChunkScanner.modelPosition(experiencePosition), "active experience-orb cluster", experienceStrength, tick, 2400);
        }
        if ((villagerStrength = EvidenceHeuristics.tradedVillagers(tradedVillagers, tradedVillagerLevels)) > 0) {
            result.addStatic(SignalCategory.INTERACTION, ChunkScanner.modelPosition(villagerPosition), "player-traded villagers x" + tradedVillagers, villagerStrength);
        }
        int totalAnimals = 0;
        int maxSameType = 0;
        for (int count : animalTypes.values()) {
            totalAnimals += count;
            maxSameType = Math.max(maxSameType, count);
        }
        if ((animalStrength = EvidenceHeuristics.pennedAnimalCluster(maxSameType, totalAnimals)) > 0) {
            result.addStatic(
                SignalCategory.CULTIVATION,
                ChunkScanner.modelPosition(animalPosition),
                "dense passive-animal cluster x" + totalAnimals,
                animalStrength
            );
        }
    }

    private static String summarize(TreeSet<String> types) {
        return types.stream().limit(6L).reduce((left, right) -> left + ", " + right).orElse("unknown");
    }

    private static BlockPosition modelPosition(BlockPos position) {
        return new BlockPosition(position.getX(), position.getY(), position.getZ());
    }

    private static long mixHash(long hash, int position, int blockPathHash) {
        long value = (long)position << 32 ^ (long)blockPathHash & 0xFFFFFFFFL;
        hash ^= value + -7046029254386353131L + (hash << 6) + (hash >>> 2);
        return hash * 1099511628211L;
    }

    @Environment(value=EnvType.CLIENT)
    public final class ChunkScanJob {
        private final ClientWorld level;
        private final WorldChunk chunk;
        private final long tick;
        private final ScanResult.Builder builder = ScanResult.builder();
        private final EnumMap<ActivityClassifier.Signal, Aggregate> aggregates = new EnumMap<>(ActivityClassifier.Signal.class);
        private final Map<Integer, Grid> cropLayers = new HashMap<Integer, Grid>();
        private final Map<Integer, Grid> berryLayers = new HashMap<Integer, Grid>();
        private final Map<Integer, Grid> farmlandLayers = new HashMap<Integer, Grid>();
        private final ArrayList<BlockPos> dripstone = new ArrayList<>();
        private final ArrayList<BlockPos> cauldrons = new ArrayList<>();
        private final Map<Long, String> currentAmethyst = new HashMap<Long, String>();
        private final KelpColumns kelpColumns = new KelpColumns();
        private final List<BlockPosition> cobbledTrailBlocks = new ArrayList<>();
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private final ChunkSection[] sections;
        private final int minX;
        private final int minZ;
        private final int observerSectionY;
        private final boolean collectConcealedLight;
        private final long[] stableHashes;
        private final int[] stableCounts;
        private final TunnelDetector.Volume tunnelVolume;
        private int sectionIndex;
        private int blockIndex;
        private int importedPlants;
        private BlockPos importedRepresentative;
        private int lightLeakCount;
        private int strongestLeakedLight;
        private BlockPos lightRepresentative;
        private int generatedDeepStructureMarkers;
        private ScanResult completed;
        private List<TunnelSegment> tunnels = List.of();
        private List<WorldObservation> worldObservations = List.of();

        private ChunkScanJob(
            ClientWorld level,
            WorldChunk chunk,
            long tick,
            int observerSectionY,
            boolean collectTunnels,
            boolean collectConcealedLight
        ) {
            this.level = level;
            this.chunk = chunk;
            this.tick = tick;
            this.observerSectionY = observerSectionY;
            this.collectConcealedLight = collectConcealedLight;
            this.sections = chunk.getSectionArray();
            this.minX = chunk.getPos().getStartX();
            this.minZ = chunk.getPos().getStartZ();
            this.stableHashes = new long[this.sections.length];
            this.stableCounts = new int[this.sections.length];
            this.tunnelVolume = collectTunnels ? new TunnelDetector.Volume(level.getBottomY(), Math.min(51, level.getTopYInclusive() - 1)) : null;
            Arrays.fill(this.stableHashes, -3750763034362895579L);
        }

        public int step(int blockBudget) {
            if (this.completed != null || blockBudget <= 0) {
                return 0;
            }
            int visited = 0;
            while (this.sectionIndex < this.sections.length) {
                ChunkSection section = this.sections[this.sectionIndex];
                if (section.isEmpty()) {
                    if (this.tunnelVolume != null) {
                        this.tunnelVolume.fillSection(ChunkSectionPos.getBlockCoord((int)this.chunk.sectionIndexToCoord(this.sectionIndex)));
                    }
                    ++this.sectionIndex;
                    this.blockIndex = 0;
                    continue;
                }
                if (this.blockIndex == 0 && this.tunnelVolume == null && !section.hasAny(ChunkScanner.this.classifier::isDetailedScanCandidate)) {
                    ++this.sectionIndex;
                    continue;
                }
                if (visited >= blockBudget) {
                    break;
                }
                int localX = this.blockIndex & 0xF;
                int localZ = this.blockIndex >>> 4 & 0xF;
                int localY = this.blockIndex >>> 8 & 0xF;
                int y = ChunkSectionPos.getBlockCoord((int)this.chunk.sectionIndexToCoord(this.sectionIndex)) + localY;
                this.cursor.set(this.minX + localX, y, this.minZ + localZ);
                this.inspect(section.getBlockState(localX, localY, localZ), localX, localY, localZ, y);
                ++visited;
                ++this.blockIndex;
                if (this.blockIndex != 4096) continue;
                ++this.sectionIndex;
                this.blockIndex = 0;
            }
            if (this.sectionIndex == this.sections.length) {
                this.finish();
            }
            return visited;
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

        public List<WorldObservation> worldObservations() {
            if (this.completed == null) {
                throw new IllegalStateException("scan is not complete");
            }
            return this.worldObservations;
        }

        public List<BlockPosition> cobbledTrailCandidates() {
            if (this.completed == null) {
                throw new IllegalStateException("scan is not complete");
            }
            return List.copyOf(this.cobbledTrailBlocks);
        }

        private void inspect(BlockState state, int localX, int localY, int localZ, int y) {
            int blockLight;
            ActivityClassifier.Signal signal;
            if (state.isAir()) {
                if (this.tunnelVolume != null) {
                    this.tunnelVolume.setOpen(localX, y, localZ);
                }
                return;
            }
            ActivityClassifier.BlockFacts facts = ChunkScanner.this.classifier.facts(state);
            if (ScannerStorageFilter.isStoragePath(facts.path())) {
                return;
            }
            if (this.tunnelVolume != null && ChunkScanner.tunnelPassable(facts.path())) {
                this.tunnelVolume.setOpen(localX, y, localZ);
            }
            if ((signal = facts.signal()) != null) {
                ++this.aggregates.computeIfAbsent(signal, ignored -> new Aggregate(this.cursor.toImmutable())).count;
            }
            if (ActivityClassifier.isCobbledDeepslateTrailPath(facts.path())
                && this.cobbledTrailBlocks.size() < 256) {
                this.cobbledTrailBlocks.add(modelPosition(this.cursor));
            }
            if (facts.crop()) {
                Grid grid = this.cropLayers.computeIfAbsent(y, ignored -> new Grid());
                grid.add(localX, localZ, (BlockPos)this.cursor);
                grid.addGrowth(facts.growthBucket());
            }
            if (facts.path().equals("sweet_berry_bush")) {
                Grid grid = this.berryLayers.computeIfAbsent(y, ignored -> new Grid());
                grid.add(localX, localZ, this.cursor);
                grid.addGrowth(facts.growthBucket());
            }
            if (facts.path().equals("kelp") || facts.path().equals("kelp_plant")) {
                this.kelpColumns.add(localX, localZ, y, facts.path().equals("kelp"), this.cursor);
            }
            if (facts.farmland()) {
                this.farmlandLayers.computeIfAbsent(y, ignored -> new Grid()).add(localX, localZ, (BlockPos)this.cursor);
            }
            if (facts.importedPlantCandidate() && ChunkScanner.this.classifier.isImportedPlant(this.level, (BlockPos)this.cursor, facts.path())) {
                ++this.importedPlants;
                if (this.importedRepresentative == null) {
                    this.importedRepresentative = this.cursor.toImmutable();
                }
            }
            if (facts.pointedDripstone() && this.dripstone.size() < 96) {
                this.dripstone.add(this.cursor.toImmutable());
            }
            if (facts.cauldron() && this.cauldrons.size() < 32) {
                this.cauldrons.add(this.cursor.toImmutable());
            }
            if (facts.amethystStage()) {
                this.currentAmethyst.put(this.cursor.asLong(), facts.path());
            }
            if (facts.temporalStable()) {
                this.stableHashes[this.sectionIndex] = ChunkScanner.mixHash(this.stableHashes[this.sectionIndex], this.blockIndex, facts.path().hashCode());
                int n = this.sectionIndex;
                this.stableCounts[n] = this.stableCounts[n] + 1;
            }
            if (facts.path().equals("reinforced_deepslate") || facts.path().equals("trial_spawner") || facts.path().equals("vault") || facts.path().equals("sculk_catalyst") || facts.path().equals("sculk_sensor") || facts.path().equals("sculk_shrieker")) {
                ++this.generatedDeepStructureMarkers;
            }
            if (this.collectConcealedLight
                && (localX & 1) == 0 && (localY & 1) == 0 && (localZ & 1) == 0
                && facts.opaqueMaskBlock()
                && (blockLight = this.level.getLightLevel(LightType.BLOCK, (BlockPos)this.cursor)) > 0) {
                ++this.lightLeakCount;
                if (blockLight > this.strongestLeakedLight) {
                    this.strongestLeakedLight = blockLight;
                    this.lightRepresentative = this.cursor.toImmutable();
                }
            }
        }

        private void finish() {
            ChunkScanner.emitBlockAggregates(this.builder, this.aggregates, this.generatedDeepStructureMarkers >= 2);
            ChunkScanner.emitFarmEvidence(this.builder, this.cropLayers, this.farmlandLayers);
            ChunkScanner.emitGrowthGeometry(
                this.builder,
                this.level,
                ChunkScanner.this.classifier,
                this.berryLayers,
                this.kelpColumns
            );
            if (this.importedPlants > 0) {
                this.builder.addStatic(SignalCategory.CULTIVATION, ChunkScanner.modelPosition(this.importedRepresentative), "plants outside their native biome x" + this.importedPlants, Math.min(110, 30 + this.importedPlants * 5));
            }
            ChunkScanner.emitDripstoneFarm(this.builder, this.dripstone, this.cauldrons);
            if (this.lightLeakCount > 0) {
                int rawStrength = Math.min(200, 80 + this.strongestLeakedLight * 6 + Math.min(40, this.lightLeakCount * 2));
                this.builder.addStatic(SignalCategory.INFRASTRUCTURE, ChunkScanner.modelPosition(this.lightRepresentative), "block light inside opaque stone mask x" + this.lightLeakCount, rawStrength);
            }
            this.worldObservations = ChunkScanner.collectWorldObservations(
                this.currentAmethyst, this.cobbledTrailBlocks
            );
            ChunkScanner.this.emitTemporalChanges(this.builder, this.chunk, this.stableHashes, this.stableCounts, this.observerSectionY, this.tick);
            ChunkScanner.this.emitBlockEntities(this.builder, this.chunk);
            ChunkScanner.emitEntities(this.builder, this.level, this.chunk, this.tick);
            if (this.tunnelVolume != null) {
                this.tunnels = TunnelDetector.detect(this.tunnelVolume, this.minX, this.minZ);
            }
            this.completed = this.builder.completeSnapshot(this.tick, this.sections.length, this.sections.length).build();
        }

    }

    @Environment(value=EnvType.CLIENT)
    private static final class Aggregate {
        private final BlockPos representative;
        private int count;

        private Aggregate(BlockPos representative) {
            this.representative = representative;
        }
    }

    @Environment(value=EnvType.CLIENT)
    private static final class Grid {
        private final boolean[] occupied = new boolean[256];
        private final int[] growthBuckets = new int[5];
        private int count;
        private int stagedCount;
        private int minX = 16;
        private int maxX = -1;
        private int minZ = 16;
        private int maxZ = -1;
        private BlockPos representative;

        private Grid() {
        }

        private void add(int x, int z, BlockPos position) {
            int index = z * 16 + x;
            if (this.occupied[index]) {
                return;
            }
            this.occupied[index] = true;
            ++this.count;
            this.minX = Math.min(this.minX, x);
            this.maxX = Math.max(this.maxX, x);
            this.minZ = Math.min(this.minZ, z);
            this.maxZ = Math.max(this.maxZ, z);
            if (this.representative == null) {
                this.representative = position.toImmutable();
            }
        }

        private int maxRun() {
            int best = 0;
            for (int fixed = 0; fixed < 16; ++fixed) {
                int row = 0;
                int column = 0;
                for (int moving = 0; moving < 16; ++moving) {
                    row = this.occupied[fixed * 16 + moving] ? row + 1 : 0;
                    column = this.occupied[moving * 16 + fixed] ? column + 1 : 0;
                    best = Math.max(best, Math.max(row, column));
                }
            }
            return best;
        }

        private void addGrowth(int bucket) {
            if (bucket >= 0 && bucket < this.growthBuckets.length) {
                int n = bucket;
                this.growthBuckets[n] = this.growthBuckets[n] + 1;
                ++this.stagedCount;
            }
        }

        private int dominantGrowthCount() {
            int best = 0;
            for (int count : this.growthBuckets) {
                best = Math.max(best, count);
            }
            return best;
        }

        private boolean isRectangular() {
            if (this.count < 6 || this.maxX - this.minX < 1 || this.maxZ - this.minZ < 1) {
                return false;
            }
            int area = (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1);
            return area <= 96 && this.count * 100 >= area * 60;
        }

    }

    @Environment(value=EnvType.CLIENT)
    private static final class KelpColumns {
        private final short[] counts = new short[256];
        private final short[] minY = new short[256];
        private final short[] maxY = new short[256];
        private final short[] topY = new short[256];
        private BlockPos representative;

        private KelpColumns() {
            Arrays.fill(this.minY, Short.MAX_VALUE);
            Arrays.fill(this.maxY, Short.MIN_VALUE);
            Arrays.fill(this.topY, Short.MIN_VALUE);
        }

        private void add(int x, int z, int y, boolean top, BlockPos position) {
            int index = z * 16 + x;
            this.counts[index]++;
            this.minY[index] = (short)Math.min(this.minY[index], y);
            this.maxY[index] = (short)Math.max(this.maxY[index], y);
            if (top) {
                this.topY[index] = (short)y;
            }
            if (this.representative == null) {
                this.representative = position.toImmutable();
            }
        }

        private int longColumns() {
            int total = 0;
            for (int index = 0; index < this.counts.length; ++index) {
                if (this.counts[index] >= 8 && this.maxY[index] - this.minY[index] + 1 == this.counts[index]) {
                    ++total;
                }
            }
            return total;
        }

        private int alignedLongColumns(int expectedTopY) {
            int total = 0;
            for (int index = 0; index < this.counts.length; ++index) {
                if (this.counts[index] < 8 || this.maxY[index] - this.minY[index] + 1 != this.counts[index]) {
                    continue;
                }
                if (Math.abs(this.topY[index] - expectedTopY) <= 1) {
                    ++total;
                }
            }
            return total;
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record ChunkSnapshot(long[] hashes, int[] counts, int observerSectionY) {
    }

    @Environment(value=EnvType.CLIENT)
    private static final class EntitySignals {
        private final Map<String, EntityAggregate> values = new HashMap<String, EntityAggregate>();

        private EntitySignals() {
        }

        private void addTamed(Entity entity) {
            this.add("tamed entities", SignalCategory.INTERACTION, 120, entity);
        }

        private void addCreated(Entity entity) {
            this.add("player-created entities", SignalCategory.INFRASTRUCTURE, 130, entity);
        }

        private void addEquipped(Entity entity) {
            this.add("saddled or body-equipped entities", SignalCategory.INTERACTION, 95, entity);
        }

        private void addLeashed(Entity entity) {
            this.add("leashed entities", SignalCategory.INTERACTION, 70, entity);
        }

        private void addDecorative(Entity entity) {
            this.add("decorative entities", SignalCategory.PLACED_BLOCK, 45, entity);
        }

        private void addVehicle(Entity entity) {
            this.add("boats or minecarts", SignalCategory.INFRASTRUCTURE, 20, entity);
        }

        private void addNamed(Entity entity) {
            this.add("custom-named entities", SignalCategory.INTERACTION, 60, entity);
        }

        private void add(String reason, SignalCategory category, int baseStrength, Entity entity) {
            EntityAggregate aggregate = this.values.computeIfAbsent(reason, ignored -> new EntityAggregate(category, baseStrength, entity.getBlockPos()));
            ++aggregate.count;
        }

        private void emit(ScanResult.Builder result) {
            for (Map.Entry<String, EntityAggregate> entry : this.values.entrySet()) {
                EntityAggregate aggregate = entry.getValue();
                result.addStatic(aggregate.category, ChunkScanner.modelPosition(aggregate.position), entry.getKey() + " x" + aggregate.count, Math.min(200, aggregate.baseStrength + (aggregate.count - 1) * 12));
            }
        }
    }

    @Environment(value=EnvType.CLIENT)
    private static final class EntityAggregate {
        private final SignalCategory category;
        private final int baseStrength;
        private final BlockPos position;
        private int count;

        private EntityAggregate(SignalCategory category, int baseStrength, BlockPos position) {
            this.category = category;
            this.baseStrength = baseStrength;
            this.position = position;
        }
    }
}
