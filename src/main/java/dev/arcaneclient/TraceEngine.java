package dev.arcaneclient;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ChunkIntel;
import dev.arcaneclient.model.ChunkTrace;
import dev.arcaneclient.model.CobbledDeepslateTrailHeuristics;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.ScanEvidence;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.ScoreSummary;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.model.StashHeuristics;
import dev.arcaneclient.model.TunnelSegment;
import dev.arcaneclient.model.WorldObservation;
import dev.arcaneclient.performance.PerformanceProfile;
import dev.arcaneclient.scan.ChunkScanner;
import dev.arcaneclient.scan.ActivityClassifier;
import dev.arcaneclient.scan.EvidenceCorrelator;
import dev.arcaneclient.scan.GrowthTransitions;
import dev.arcaneclient.scan.ScanScheduling;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(value=EnvType.CLIENT)
public final class TraceEngine {
    private static final int MAX_REMEMBERED_CHUNKS = 12000;
    private static final int LIVE_DECAY_TICKS = 9600;
    private static final int SCAN_SLICE_BLOCKS = 2048;
    private static final int MAX_SCAN_SLICES_PER_TICK = 128;
    private static final int MAX_ACTIVITY_CLUSTERS = 24;
    private static final int MAX_WORLD_OBSERVATIONS = 2048;
    private final ArcaneConfig config;
    private final ChunkScanner scanner = new ChunkScanner();
    private final EvidenceCorrelator correlator = new EvidenceCorrelator();
    private final LinkedHashMap<TraceKey, ChunkTrace> traces = new LinkedHashMap<>();
    private final PriorityQueue<QueuedScan> scanQueue;
    private final Map<TraceKey, QueuedScan> queued = new HashMap<>();
    private final ArrayDeque<ActiveScan> activeScans = new ArrayDeque<>();
    private final Set<TraceKey> activeScanKeys = new HashSet<TraceKey>();
    private final Map<TraceKey, Integer> grownCounts = new HashMap<>();
    private final Map<TraceKey, Integer> scannedChunks = new HashMap<TraceKey, Integer>();
    private final Map<EventKey, Long> eventCooldowns = new HashMap<EventKey, Long>();
    private final Map<TraceKey, DirtyChunk> dirtyChunks = new HashMap<TraceKey, DirtyChunk>();
    private final Map<TraceKey, List<TunnelSegment>> tunnelSegments = new HashMap<TraceKey, List<TunnelSegment>>();
    private final Map<TraceKey, List<WorldObservation>> worldObservationChunks = new HashMap<>();
    private final LinkedHashMap<LiveWorldKey, TimedWorldObservation> liveWorldObservations = new LinkedHashMap<>();
    private final Map<TraceKey, List<BlockPosition>> cobbledTrailCandidateChunks = new HashMap<>();
    private final LinkedHashMap<LiveWorldKey, TimedWorldObservation> liveTrailCandidates = new LinkedHashMap<>();
    private long tick;
    private long queueSequence;
    private String session = "menu";
    private String dimension = "unknown";
    private @Nullable ClientLevel level;
    private int priorityCenterX = Integer.MIN_VALUE;
    private int priorityCenterZ = Integer.MIN_VALUE;
    private int priorityDirectionX;
    private int priorityDirectionZ;
    private volatile List<ChunkMarker> markerSnapshot = List.of();
    private volatile List<ChunkTile> tileSnapshot = List.of();
    private volatile Map<Long, ChunkMarker> scoreIndexSnapshot = Map.of();
    private volatile int flaggedMarkerCount;
    private volatile List<TunnelSegment> tunnelSnapshot = List.of();
    private volatile List<ActivityClusterCandidate> activityClusterSnapshot = List.of();
    private volatile List<WorldObservation> worldObservationSnapshot = List.of();

    public TraceEngine(ArcaneConfig config) {
        this.config = config;
        this.scanQueue = new PriorityQueue<>(this::compareQueuedScans);
    }

    public void onWorldChange(Minecraft client, @Nullable ClientLevel newLevel) {
        this.level = newLevel;
        this.scanner.resetTemporalHistory();
        this.scanQueue.clear();
        this.queued.clear();
        this.activeScans.clear();
        this.activeScanKeys.clear();
        this.scannedChunks.clear();
        this.grownCounts.clear();
        this.eventCooldowns.clear();
        this.dirtyChunks.clear();
        this.correlator.reset();
        this.tunnelSegments.clear();
        this.worldObservationChunks.clear();
        this.liveWorldObservations.clear();
        this.cobbledTrailCandidateChunks.clear();
        this.liveTrailCandidates.clear();
        this.queueSequence = 0L;
        this.priorityCenterX = Integer.MIN_VALUE;
        this.priorityCenterZ = Integer.MIN_VALUE;
        this.priorityDirectionX = 0;
        this.priorityDirectionZ = 0;
        this.markerSnapshot = List.of();
        this.tileSnapshot = List.of();
        this.scoreIndexSnapshot = Map.of();
        this.flaggedMarkerCount = 0;
        this.tunnelSnapshot = List.of();
        this.activityClusterSnapshot = List.of();
        this.worldObservationSnapshot = List.of();
        if (newLevel == null) {
            this.session = "menu";
            this.dimension = "unknown";
            return;
        }
        this.session = TraceEngine.sessionId(client);
        this.dimension = newLevel.dimension().identifier().toString();
        this.queueNearby(client);
    }

    public void onChunkLoad(ClientLevel world, LevelChunk chunk) {
        if (world == this.level && this.scanningEnabled()) {
            this.enqueue(chunk.getPos().x(), chunk.getPos().z(), true);
        }
    }

    public void onChunkUnload(ClientLevel world, LevelChunk chunk) {
        if (world == this.level) {
            TraceKey traceKey = this.key(chunk.getPos().x(), chunk.getPos().z());
            this.queued.remove(traceKey);
            this.activeScanKeys.remove(traceKey);
            this.activeScans.removeIf(scan -> scan.key.equals(traceKey));
            this.scannedChunks.remove(traceKey);
            this.grownCounts.remove(traceKey);
            this.traces.remove(traceKey);
            this.dirtyChunks.remove(traceKey);
            this.correlator.forgetChunk(chunk.getPos().x(), chunk.getPos().z());
            this.tunnelSegments.remove(traceKey);
            this.worldObservationChunks.remove(traceKey);
            this.liveWorldObservations.keySet().removeIf(key -> key.traceKey().equals(traceKey));
            this.cobbledTrailCandidateChunks.remove(traceKey);
            this.liveTrailCandidates.keySet().removeIf(key -> key.traceKey().equals(traceKey));
            this.scanner.forgetChunk(chunk);
        }
    }

    public void tick(Minecraft client) {
        ++this.tick;
        if (client.level != this.level) {
            this.onWorldChange(client, client.level);
        }
        if (!this.scanningEnabled() || client.level == null || client.player == null) {
            return;
        }
        this.updateScanPriority(client);
        this.flushDirtyScans();
        if (this.tick % ((long)this.config.rescanSeconds * 20L) == 0L) {
            this.queueNearby(client);
        }
        this.startScanJobs(client);
        this.processScanJobs(client);
        int snapshotRefreshTicks = this.config.performanceProfile().snapshotRefreshTicks();
        if (this.tick % snapshotRefreshTicks == 0L) {
            this.refreshMarkers(client);
            this.refreshTunnelSnapshot(client);
        }
        if (this.tick % 1200L == 0L) {
            this.pruneCooldowns();
        }
    }

    public void queueNearby(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        this.updateScanPriority(client);
        ChunkPos center = client.player.chunkPosition();
        int observerSectionY = SectionPos.blockToSectionCoord(client.player.getBlockY());
        for (int dz = -this.config.scanRadius; dz <= this.config.scanRadius; ++dz) {
            for (int dx = -this.config.scanRadius; dx <= this.config.scanRadius; ++dx) {
                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;
                if (client.level.getChunkSource().getChunk(chunkX, chunkZ, false) == null) continue;
                TraceKey traceKey = this.key(chunkX, chunkZ);
                boolean frontier = !this.scannedChunks.containsKey(traceKey);
                this.enqueue(chunkX, chunkZ, frontier);
            }
        }
    }

    public void clearCurrent() {
        this.traces.keySet().removeIf(this::isCurrent);
        this.scanQueue.clear();
        this.queued.clear();
        this.activeScans.clear();
        this.activeScanKeys.clear();
        this.scannedChunks.clear();
        this.grownCounts.clear();
        this.dirtyChunks.clear();
        this.correlator.reset();
        this.tunnelSegments.clear();
        this.worldObservationChunks.clear();
        this.liveWorldObservations.clear();
        this.cobbledTrailCandidateChunks.clear();
        this.liveTrailCandidates.clear();
        this.scanner.resetTemporalHistory();
        this.markerSnapshot = List.of();
        this.tileSnapshot = List.of();
        this.scoreIndexSnapshot = Map.of();
        this.flaggedMarkerCount = 0;
        this.tunnelSnapshot = List.of();
        this.activityClusterSnapshot = List.of();
        this.worldObservationSnapshot = List.of();
    }

    public void recordLive(BlockPos pos, SignalCategory category, int strength, String reason) {
        this.recordLive(pos, category, strength, reason, 9600, 10);
    }

    public void recordLive(BlockPos pos, SignalCategory category, int strength, String reason, int decayTicks, int cooldownTicks) {
        // Uncorrelated sounds, block events and entities are not plant-growth evidence.
        if (category != SignalCategory.GROWTH_ACTIVITY && category != SignalCategory.PLANT_HARVEST) return;
        if (!this.config.enabled || this.level == null || strength <= 0) {
            return;
        }
        BlockPosition blockPosition = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        EventKey eventKey = new EventKey(blockPosition, category, reason);
        Long previous = this.eventCooldowns.put(eventKey, this.tick);
        if (previous != null && this.tick - previous < (long)cooldownTicks) {
            return;
        }
        ScanResult result = ScanResult.builder().addLive(category, blockPosition, reason, strength, this.tick, decayTicks).build();
        this.merge(this.key(blockPosition.chunkX(), blockPosition.chunkZ()), result);
    }

    /** Called only after the packet bridge has rejected storage-bearing old and new states. */
    public void recordAllowedBlockUpdate(
        BlockPos pos,
        GrowthTransitions.GrowthEvent growth,
        boolean automationChanged
    ) {
        if ((!this.config.enabled && !this.config.amethystEsp) || this.level == null) return;
        BlockPosition position = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        TraceKey traceKey = this.key(position.chunkX(), position.chunkZ());
        this.dirtyChunks.computeIfAbsent(traceKey, ignored -> new DirtyChunk())
            .mark(SectionPos.blockToSectionCoord(pos.getY()), this.tick);
        // Plant changes invalidate the count. No event accumulation or hidden timing gate.
    }

    public void recordAmethystState(BlockPos pos, String beforeId, String afterId) {
        if ((!this.config.enabled && !this.config.amethystEsp) || this.level == null) return;
        int before = GrowthTransitions.amethystRank(beforeId);
        int after = GrowthTransitions.amethystRank(afterId);
        BlockPosition position = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        TraceKey traceKey = this.key(position.chunkX(), position.chunkZ());
        LiveWorldKey key = new LiveWorldKey(traceKey, position, WorldObservation.Kind.AMETHYST_SHARD);
        if (after >= 0) {
            this.liveWorldObservations.put(
                key,
                new TimedWorldObservation(
                    new WorldObservation(position, WorldObservation.Kind.AMETHYST_SHARD, 100, true, after),
                    this.tick + 12000L
                )
            );
            this.trimLiveWorldObservations();
        } else if (before >= 0) {
            this.removeStaticWorldObservation(traceKey, position, WorldObservation.Kind.AMETHYST_SHARD);
            this.liveWorldObservations.remove(key);
        }
    }

    public void recordAmethystBreak(BlockPos pos, String blockId) {
        if ((!this.config.enabled && !this.config.amethystEsp) || this.level == null) return;
        int stage = GrowthTransitions.amethystRank(blockId);
        if (stage < 0) return;
        BlockPosition position = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        TraceKey traceKey = this.key(position.chunkX(), position.chunkZ());
        LiveWorldKey key = new LiveWorldKey(traceKey, position, WorldObservation.Kind.AMETHYST_SHARD);
        this.liveWorldObservations.remove(key);
        this.removeStaticWorldObservation(traceKey, position, WorldObservation.Kind.AMETHYST_SHARD);
    }

    public void recordCobbledDeepslateState(BlockPos pos, String beforeId, String afterId) {
        if ((!this.config.enabled && !this.config.accessTrailEsp) || this.level == null) return;
        boolean before = ActivityClassifier.isCobbledDeepslateTrailPath(beforeId);
        boolean after = ActivityClassifier.isCobbledDeepslateTrailPath(afterId);
        if (!before && !after) return;
        this.recordCobbledDeepslateCandidate(pos, after ? 100 : 90, after ? 12000 : 4800);
    }

    public void recordCobbledDeepslateHint(BlockPos pos, int confidence) {
        if ((!this.config.enabled && !this.config.accessTrailEsp) || this.level == null) return;
        this.recordCobbledDeepslateCandidate(pos, confidence, 4800);
    }

    private void recordCobbledDeepslateCandidate(BlockPos pos, int confidence, int lifetimeTicks) {
        BlockPosition position = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        TraceKey traceKey = this.key(position.chunkX(), position.chunkZ());
        LiveWorldKey key = new LiveWorldKey(traceKey, position, WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL);
        TimedWorldObservation incoming = new TimedWorldObservation(
            new WorldObservation(position, WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL, confidence, true),
            this.tick + lifetimeTicks
        );
        this.liveTrailCandidates.merge(key, incoming, TraceEngine::strongerTimedObservation);
        trimTimedObservations(this.liveTrailCandidates);
    }

    public void mergeStatic(int chunkX, int chunkZ, ScanResult result) {
        if (this.config.enabled && this.level != null) {
            this.merge(this.key(chunkX, chunkZ), result);
        }
    }

    public long currentTick() {
        return this.tick;
    }

    public boolean recordsConcealedLight() {
        return false;
    }

    public int queueSize() {
        return this.queued.size() + this.activeScans.size();
    }

    public int flaggedCount() {
        return this.flaggedMarkerCount;
    }

    public boolean hasScanBaseline(int chunkX, int chunkZ, int observerSectionY) {
        return this.level != null && this.scannedChunks.containsKey(this.key(chunkX, chunkZ));
    }

    public List<ChunkMarker> markers() {
        return this.markerSnapshot;
    }

    /** Completed nearby scans, including quiet chunks, for the world-space tile overlay. */
    public List<ChunkTile> tiles() {
        return this.tileSnapshot;
    }

    public List<TunnelSegment> tunnels() {
        return this.tunnelSnapshot;
    }

    public List<ActivityClusterCandidate> activityClusters() {
        return this.activityClusterSnapshot;
    }

    public List<WorldObservation> worldObservations() {
        return this.worldObservationSnapshot;
    }

    public int worldObservationCount(WorldObservation.Kind kind) {
        int count = 0;
        for (WorldObservation observation : this.worldObservationSnapshot) {
            if (observation.kind() == kind) count++;
        }
        return count;
    }

    public int amethystStageCount(int stage) {
        int count = 0;
        for (WorldObservation observation : this.worldObservationSnapshot) {
            if (observation.kind() == WorldObservation.Kind.AMETHYST_SHARD && observation.stage() == stage) count++;
        }
        return count;
    }

    public int activityClusterCount() {
        return this.activityClusterSnapshot.size();
    }

    public void settingsChanged(Minecraft client) {
        if (this.level != null && client.player != null) {
            if (this.config.evidencePoints || this.config.amethystEsp || this.config.accessTrailEsp || this.config.tunnelEsp) {
                this.queueNearby(client);
            }
            this.refreshMarkers(client);
            this.refreshTunnelSnapshot(client);
        }
    }

    public void tunnelSettingsChanged(Minecraft client) {
        this.tunnelSnapshot = List.of();
        this.tunnelSegments.clear();
        if (!this.config.tunnelEsp) {
            if (!this.config.enabled) {
                this.activeScans.clear();
                this.activeScanKeys.clear();
                this.scanQueue.clear();
                this.queued.clear();
            }
            return;
        }
        this.activeScans.clear();
        this.activeScanKeys.clear();
        this.scanQueue.clear();
        this.queued.clear();
        this.queueNearby(client);
    }

    public @Nullable ChunkMarker markerAt(int chunkX, int chunkZ) {
        TraceKey traceKey = this.key(chunkX, chunkZ);
        ChunkTrace trace = this.traces.get(traceKey);
        return trace == null ? null : this.marker(traceKey, trace);
    }

    public List<ChunkMarker> nearby(int centerX, int centerZ, int radius) {
        ArrayList<ChunkMarker> nearby = new ArrayList<>(Math.min((radius * 2 + 1) * (radius * 2 + 1), 64));
        Map<Long, ChunkMarker> index = this.scoreIndexSnapshot;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkMarker marker = index.get(chunkKey(centerX + dx, centerZ + dz));
                if (marker != null) nearby.add(marker);
            }
        }
        return List.copyOf(nearby);
    }

    public @Nullable ChunkMarker snapshotMarkerAt(int chunkX, int chunkZ) {
        return this.scoreIndexSnapshot.get(chunkKey(chunkX, chunkZ));
    }

    public List<ChunkMarker> top(int count) {
        ArrayList<ChunkMarker> markers = new ArrayList<ChunkMarker>();
        for (Map.Entry<TraceKey, ChunkTrace> entry : this.traces.entrySet()) {
            if (!this.isCurrent(entry.getKey())) continue;
            markers.add(this.marker(entry.getKey(), entry.getValue()));
        }
        markers.sort(Comparator.comparingInt(ChunkMarker::score).reversed().thenComparingInt(ChunkMarker::chunkX).thenComparingInt(ChunkMarker::chunkZ));
        return List.copyOf(markers.subList(0, Math.min(count, markers.size())));
    }

    private void enqueue(int x, int z, boolean frontier) {
        TraceKey traceKey = this.key(x, z);
        if (this.activeScanKeys.contains(traceKey)) {
            return;
        }
        QueuedScan existing = this.queued.get(traceKey);
        if (existing != null && (!frontier || existing.frontier)) {
            return;
        }
        long sequence = existing == null ? ++this.queueSequence : existing.sequence;
        QueuedScan queuedScan = new QueuedScan(traceKey, frontier, sequence);
        this.queued.put(traceKey, queuedScan);
        this.scanQueue.add(queuedScan);
    }

    private void flushDirtyScans() {
        Iterator<Map.Entry<TraceKey, DirtyChunk>> iterator = this.dirtyChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TraceKey, DirtyChunk> entry = iterator.next();
            TraceKey key = entry.getKey();
            DirtyChunk dirty = entry.getValue();
            if (!this.isCurrent(key)) {
                iterator.remove();
                continue;
            }
            if ((this.tick - dirty.lastUpdateTick < 8L && this.tick - dirty.firstUpdateTick < 20L)
                || this.activeScanKeys.contains(key)) continue;
            this.enqueue(key.x, key.z, true);
            iterator.remove();
        }
    }

    private void startScanJobs(Minecraft client) {
        int targetActive = ScanScheduling.activeWindow(this.config.chunksPerTick);
        while (true) {
            QueuedScan next = this.peekQueuedScan();
            int activeLimit = targetActive + (next != null && next.frontier ? 2 : 0);
            if (next == null || this.activeScans.size() >= activeLimit) {
                this.prioritizeActiveScans();
                return;
            }
            LevelChunk chunk;
            this.scanQueue.poll();
            if (!this.queued.remove(next.key, next)) continue;
            TraceKey traceKey = next.key;
            if (!this.isCurrent(traceKey)
                || !this.withinScanRadius(client, traceKey)
                || (chunk = client.level.getChunkSource().getChunk(traceKey.x, traceKey.z, false)) == null) continue;
            try {
                int observerSectionY = SectionPos.blockToSectionCoord((int)client.player.blockPosition().getY());
                this.activeScans.addLast(new ActiveScan(
                    traceKey,
                    this.scanner.begin(
                        client.level,
                        chunk,
                        this.tick,
                        observerSectionY,
                        this.config.tunnelEsp,
                        this.config.amethystEsp,
                        this.config.accessTrailEsp,
                        this.config.evidencePoints
                    ),
                    next.frontier,
                    next.sequence
                ));
                this.activeScanKeys.add(traceKey);
            }
            catch (RuntimeException exception) {
                ArcaneClient.LOGGER.warn("Could not start chunk scan at {}, {}", new Object[]{traceKey.x, traceKey.z, exception});
            }
        }
    }

    private void processScanJobs(Minecraft client) {
        long started = System.nanoTime();
        long deadline = started + this.scanBudgetNanos(client);
        int slices = 0;
        while (!(this.activeScans.isEmpty() || slices >= MAX_SCAN_SLICES_PER_TICK || slices > 0 && System.nanoTime() >= deadline)) {
            ActiveScan active = this.activeScans.removeFirst();
            if (!this.isCurrent(active.key) || !this.withinScanRadius(client, active.key)) {
                this.activeScanKeys.remove(active.key);
                continue;
            }
            try {
                boolean completed = false;
                for (int turnSlice = 0;
                     turnSlice < ScanScheduling.SLICES_PER_JOB_TURN
                         && slices < MAX_SCAN_SLICES_PER_TICK
                         && (slices == 0 || System.nanoTime() < deadline);
                     ++turnSlice) {
                    active.job.step(SCAN_SLICE_BLOCKS);
                    ++slices;
                    if (!active.job.isComplete()) continue;
                    completed = true;
                    this.activeScanKeys.remove(active.key);
                    this.scannedChunks.put(active.key, active.job.observerSectionY());
                    this.grownCounts.put(active.key, active.job.grownCount());
                    if (this.config.tunnelEsp) {
                        List<TunnelSegment> found = active.job.tunnels();
                        if (found.isEmpty()) {
                            this.tunnelSegments.remove(active.key);
                        } else {
                            this.tunnelSegments.put(active.key, found);
                        }
                    }
                    List<WorldObservation> observations = active.job.worldObservations();
                    if (observations.isEmpty()) {
                        this.worldObservationChunks.remove(active.key);
                    } else {
                        this.worldObservationChunks.put(active.key, observations);
                    }
                    List<BlockPosition> trailCandidates = active.job.cobbledTrailCandidates();
                    if (trailCandidates.isEmpty()) {
                        this.cobbledTrailCandidateChunks.remove(active.key);
                    } else {
                        this.cobbledTrailCandidateChunks.put(active.key, trailCandidates);
                    }
                    this.merge(active.key, active.job.result());
                    break;
                }
                if (!completed) {
                    this.activeScans.addLast(active);
                }
            }
            catch (RuntimeException exception) {
                this.activeScanKeys.remove(active.key);
                ArcaneClient.LOGGER.warn("Chunk scan failed at {}, {}", new Object[]{active.key.x, active.key.z, exception});
            }
        }
    }

    private void updateScanPriority(Minecraft client) {
        ChunkPos center = client.player.chunkPosition();
        int directionX = ScanScheduling.direction(client.player.getDeltaMovement().x);
        int directionZ = ScanScheduling.direction(client.player.getDeltaMovement().z);
        if (center.x() == this.priorityCenterX
            && center.z() == this.priorityCenterZ
            && directionX == this.priorityDirectionX
            && directionZ == this.priorityDirectionZ) {
            return;
        }
        this.priorityCenterX = center.x();
        this.priorityCenterZ = center.z();
        this.priorityDirectionX = directionX;
        this.priorityDirectionZ = directionZ;
        this.scanQueue.clear();
        this.scanQueue.addAll(this.queued.values());
        this.prioritizeActiveScans();
    }

    private @Nullable QueuedScan peekQueuedScan() {
        while (true) {
            QueuedScan queuedScan = this.scanQueue.peek();
            if (queuedScan == null || this.queued.get(queuedScan.key) == queuedScan) {
                return queuedScan;
            }
            this.scanQueue.poll();
        }
    }

    private void prioritizeActiveScans() {
        if (this.activeScans.size() < 2) {
            return;
        }
        ArrayList<ActiveScan> prioritized = new ArrayList<>(this.activeScans);
        prioritized.sort(this::compareActiveScans);
        this.activeScans.clear();
        this.activeScans.addAll(prioritized);
    }

    private int compareQueuedScans(QueuedScan left, QueuedScan right) {
        int compared = Long.compare(this.priority(left.key, left.frontier), this.priority(right.key, right.frontier));
        return compared != 0 ? compared : Long.compare(left.sequence, right.sequence);
    }

    private int compareActiveScans(ActiveScan left, ActiveScan right) {
        int compared = Long.compare(this.priority(left.key, left.frontier), this.priority(right.key, right.frontier));
        return compared != 0 ? compared : Long.compare(left.sequence, right.sequence);
    }

    private long priority(TraceKey traceKey, boolean frontier) {
        int centerX = this.priorityCenterX == Integer.MIN_VALUE ? traceKey.x : this.priorityCenterX;
        int centerZ = this.priorityCenterZ == Integer.MIN_VALUE ? traceKey.z : this.priorityCenterZ;
        return ScanScheduling.priorityScore(
            frontier,
            traceKey.x,
            traceKey.z,
            centerX,
            centerZ,
            this.priorityDirectionX,
            this.priorityDirectionZ
        );
    }

    private long scanBudgetNanos(Minecraft client) {
        long profileBudget = this.config.performanceProfile().scanBudgetNanos(this.config.chunksPerTick);
        return ArcaneSettingsScreen.isOpen(client) ? Math.min(500_000L, profileBudget) : profileBudget;
    }

    private void merge(TraceKey traceKey, ScanResult result) {
        ScanResult.Builder plants = ScanResult.builder().observedAt(result.observedAtTick());
        if (result.completeSnapshot()) plants.completeSnapshot(result.observedAtTick(), result.scannedSections(), result.totalSections());
        for (ScanEvidence evidence : result.evidence()) {
            if (allowsEvidence(evidence)) plants.add(evidence);
        }
        result = plants.build();
        if (result.evidence().isEmpty() && !result.completeSnapshot()) {
            return;
        }
        this.traces.computeIfAbsent(traceKey, ignored -> new ChunkTrace()).merge(result, this.tick);
        while (this.traces.size() > 12000) {
            Iterator<TraceKey> iterator = this.traces.keySet().iterator();
            if (!iterator.hasNext()) continue;
            TraceKey removed = iterator.next();
            iterator.remove();
            this.grownCounts.remove(removed);
            this.tunnelSegments.remove(removed);
            this.worldObservationChunks.remove(removed);
            this.liveWorldObservations.keySet().removeIf(key -> key.traceKey().equals(removed));
            this.cobbledTrailCandidateChunks.remove(removed);
            this.liveTrailCandidates.keySet().removeIf(key -> key.traceKey().equals(removed));
        }
    }

    private void refreshMarkers(Minecraft client) {
        this.refreshWorldObservationSnapshot(client);
        if (!this.config.enabled || client.player == null) {
            this.markerSnapshot = List.of();
            this.tileSnapshot = List.of();
            this.scoreIndexSnapshot = Map.of();
            this.flaggedMarkerCount = 0;
            this.activityClusterSnapshot = List.of();
            return;
        }
        ChunkPos center = client.player.chunkPosition();
        int renderRadius = Math.max(32, this.config.scanRadius + 4);
        ArrayList<ChunkMarker> raw = new ArrayList<ChunkMarker>();
        for (Map.Entry<TraceKey, ChunkTrace> entry : this.traces.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey) || Math.abs(traceKey.x - center.x()) > renderRadius || Math.abs(traceKey.z - center.z()) > renderRadius) continue;
            ChunkMarker marker = this.marker(traceKey, entry.getValue());
            if (marker.score > 0) {
                raw.add(marker);
            }
        }
        ArrayList<ChunkMarker> markers = new ArrayList<ChunkMarker>();
        ArrayList<ChunkMarker> scored = new ArrayList<ChunkMarker>(raw.size());
        for (ChunkMarker marker : raw) {
            scored.add(marker);
            if (marker.score >= dev.arcaneclient.scan.GrowthCountPolicy.FLAG_SCORE) {
                markers.add(marker);
            }
        }
        markers.sort(Comparator.comparingInt(ChunkMarker::score).reversed());
        scored.sort(Comparator.comparingInt(ChunkMarker::score).reversed());
        this.flaggedMarkerCount = markers.size();
        HashMap<Long, ChunkMarker> scoreIndex = new HashMap<>(Math.max(16, scored.size() * 4 / 3 + 1));
        for (ChunkMarker marker : scored) {
            scoreIndex.putIfAbsent(chunkKey(marker.chunkX, marker.chunkZ), marker);
        }
        this.scoreIndexSnapshot = Map.copyOf(scoreIndex);
        int markerLimit = this.config.performanceProfile().markerTargetLimit();
        this.markerSnapshot = List.copyOf(markers.subList(0, Math.min(markerLimit, markers.size())));
        ArrayList<ChunkTile> tiles = new ArrayList<>();
        for (TraceKey scanned : this.scannedChunks.keySet()) {
            if (!this.isCurrent(scanned)
                || Math.abs(scanned.x - center.x()) > renderRadius
                || Math.abs(scanned.z - center.z()) > renderRadius) continue;
            ChunkMarker marker = scoreIndex.get(chunkKey(scanned.x, scanned.z));
            int score = marker == null ? 0 : marker.score;
            tiles.add(new ChunkTile(scanned.x, scanned.z, score, score > 0 && score >= dev.arcaneclient.scan.GrowthCountPolicy.FLAG_SCORE));
        }
        tiles.sort(Comparator.comparingInt(tile ->
            Math.max(Math.abs(tile.chunkX - center.x()), Math.abs(tile.chunkZ - center.z()))
        ));
        this.tileSnapshot = List.copyOf(tiles.subList(0, Math.min(markerLimit, tiles.size())));
        this.refreshActivityClusterSnapshot(scored, scoreIndex);
    }

    static int growthScore(ChunkMarker marker) {
        return marker.categoryBreakdown.getOrDefault(SignalCategory.GROWN_PLANTS, 0);
    }

    private void refreshActivityClusterSnapshot(List<ChunkMarker> scored, Map<Long, ChunkMarker> byChunk) {
        if (!this.config.clusterInference) {
            this.activityClusterSnapshot = List.of();
            return;
        }
        ArrayList<ActivityClusterCandidate> candidates = new ArrayList<>();
        for (ChunkMarker marker : scored) {
            boolean direct = marker.score >= 55 && growthScore(marker) >= 45;
            List<Long> members = direct ? List.of(chunkKey(marker.chunkX, marker.chunkZ))
                : legacyClusterMembers(marker, byChunk);
            boolean clustered = members.size() > 1;
            if ((!direct && !clustered) || nearExistingCandidate(marker, candidates)) continue;
            candidates.add(new ActivityClusterCandidate(
                marker.chunkX,
                marker.chunkZ,
                marker.score,
                members.size(),
                StashHeuristics.independentCategories(marker.categoryBreakdown),
                growthScore(marker),
                members
            ));
            if (candidates.size() >= MAX_ACTIVITY_CLUSTERS) break;
        }
        this.activityClusterSnapshot = List.copyOf(candidates);
    }

    private static List<Long> legacyClusterMembers(ChunkMarker marker, Map<Long, ChunkMarker> byChunk) {
        if (growthScore(marker) < 35) {
            return List.of(chunkKey(marker.chunkX, marker.chunkZ));
        }
        ArrayList<Long> members = new ArrayList<>();
        members.add(chunkKey(marker.chunkX, marker.chunkZ));
        for (int dz = -2; dz <= 2; ++dz) {
            for (int dx = -2; dx <= 2; ++dx) {
                if (dx == 0 && dz == 0) continue;
                ChunkMarker neighbor = byChunk.get(chunkKey(marker.chunkX + dx, marker.chunkZ + dz));
                if (neighbor == null || growthScore(neighbor) < 35 || marker.score + neighbor.score < 100) continue;
                members.add(chunkKey(neighbor.chunkX, neighbor.chunkZ));
            }
        }
        members.sort(Long::compare);
        return List.copyOf(members);
    }

    private static boolean nearExistingCandidate(ChunkMarker marker, List<ActivityClusterCandidate> candidates) {
        for (ActivityClusterCandidate candidate : candidates) {
            if (Math.abs(marker.chunkX - candidate.chunkX) <= 2
                && Math.abs(marker.chunkZ - candidate.chunkZ) <= 2) {
                return true;
            }
        }
        return false;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    private void refreshTunnelSnapshot(Minecraft client) {
        if (!this.config.tunnelEsp || client.player == null) {
            this.tunnelSnapshot = List.of();
            return;
        }
        ChunkPos center = client.player.chunkPosition();
        PerformanceProfile profile = this.config.performanceProfile();
        int tunnelLimit = profile.tunnelTargetLimit();
        ArrayList<TunnelSegment> visible = new ArrayList<>(Math.min(tunnelLimit, 256));
        for (Map.Entry<TraceKey, List<TunnelSegment>> entry : this.tunnelSegments.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey) || Math.abs(traceKey.x - center.x()) > this.config.scanRadius || Math.abs(traceKey.z - center.z()) > this.config.scanRadius) continue;
            for (TunnelSegment segment : entry.getValue()) {
                visible.add(segment);
                if (visible.size() < tunnelLimit) continue;
                this.tunnelSnapshot = List.copyOf(visible);
                return;
            }
        }
        this.tunnelSnapshot = List.copyOf(visible);
    }

    private void refreshWorldObservationSnapshot(Minecraft client) {
        if (client.player == null || (!this.config.amethystEsp && !this.config.accessTrailEsp)) {
            this.worldObservationSnapshot = List.of();
            return;
        }
        ChunkPos center = client.player.chunkPosition();
        int renderRadius = Math.max(32, this.config.scanRadius + 4);
        LinkedHashMap<ObservationIdentity, WorldObservation> visible = new LinkedHashMap<>();
        for (Map.Entry<TraceKey, List<WorldObservation>> entry : this.worldObservationChunks.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey)
                || Math.abs(traceKey.x() - center.x()) > renderRadius
                || Math.abs(traceKey.z() - center.z()) > renderRadius) continue;
            for (WorldObservation observation : entry.getValue()) {
                if (observation.kind() == WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL) continue;
                mergeWorldObservation(visible, observation);
            }
        }
        Iterator<Map.Entry<LiveWorldKey, TimedWorldObservation>> iterator = this.liveWorldObservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LiveWorldKey, TimedWorldObservation> entry = iterator.next();
            LiveWorldKey key = entry.getKey();
            TimedWorldObservation timed = entry.getValue();
            if (!this.isCurrent(key.traceKey()) || timed.expiresAtTick() <= this.tick) {
                iterator.remove();
                continue;
            }
            if (Math.abs(key.traceKey().x() - center.x()) > renderRadius
                || Math.abs(key.traceKey().z() - center.z()) > renderRadius) continue;
            mergeWorldObservation(visible, timed.observation());
        }
        LinkedHashMap<BlockPosition, Boolean> trailCandidates = new LinkedHashMap<>();
        Iterator<Map.Entry<LiveWorldKey, TimedWorldObservation>> trailIterator = this.liveTrailCandidates.entrySet().iterator();
        while (trailIterator.hasNext()) {
            Map.Entry<LiveWorldKey, TimedWorldObservation> entry = trailIterator.next();
            LiveWorldKey key = entry.getKey();
            TimedWorldObservation timed = entry.getValue();
            if (!this.isCurrent(key.traceKey()) || timed.expiresAtTick() <= this.tick) {
                trailIterator.remove();
                continue;
            }
            if (Math.abs(key.traceKey().x() - center.x()) > renderRadius
                || Math.abs(key.traceKey().z() - center.z()) > renderRadius) continue;
            trailCandidates.put(key.position(), true);
        }
        for (Map.Entry<TraceKey, List<BlockPosition>> entry : this.cobbledTrailCandidateChunks.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey)
                || Math.abs(traceKey.x() - center.x()) > renderRadius
                || Math.abs(traceKey.z() - center.z()) > renderRadius) continue;
            for (BlockPosition candidate : entry.getValue()) trailCandidates.putIfAbsent(candidate, false);
        }
        for (CobbledDeepslateTrailHeuristics.Trail trail :
            CobbledDeepslateTrailHeuristics.detect(trailCandidates.keySet(), 32)) {
            for (BlockPosition point : trail.points()) {
                mergeWorldObservation(visible, new WorldObservation(
                    point,
                    WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL,
                    trail.confidence(),
                    trailCandidates.getOrDefault(point, false)
                ));
            }
        }
        // A scan may finish after a block update. Never draw a remembered stage as a current one.
        visible.values().removeIf(observation -> {
            if (observation.kind() != WorldObservation.Kind.AMETHYST_SHARD) return false;
            BlockPosition pos = observation.position();
            var chunk = client.level.getChunkSource().getChunk(pos.chunkX(), pos.chunkZ(), false);
            return chunk == null || GrowthTransitions.amethystRank(ActivityClassifier.blockPath(
                chunk.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z())))) != observation.stage();
        });
        ArrayList<WorldObservation> sorted = new ArrayList<>(visible.values());
        sorted.sort(Comparator.comparing(WorldObservation::live).reversed()
            .thenComparing(Comparator.comparingInt(WorldObservation::confidence).reversed())
            .thenComparing(observation -> observation.kind().ordinal())
            .thenComparingInt(observation -> observation.position().x())
            .thenComparingInt(observation -> observation.position().y())
            .thenComparingInt(observation -> observation.position().z()));
        this.worldObservationSnapshot = List.copyOf(sorted.subList(0, Math.min(MAX_WORLD_OBSERVATIONS, sorted.size())));
    }

    private static void mergeWorldObservation(
        Map<ObservationIdentity, WorldObservation> observations,
        WorldObservation incoming
    ) {
        ObservationIdentity identity = new ObservationIdentity(incoming.position(), incoming.kind());
        observations.merge(identity, incoming, (existing, replacement) -> {
            boolean replacementIsNewerExact = replacement.live() && replacement.stage() >= 0;
            int stage = replacementIsNewerExact || existing.stage() < 0 ? replacement.stage() : existing.stage();
            return new WorldObservation(
                existing.position(), existing.kind(),
                Math.max(existing.confidence(), replacement.confidence()),
                existing.live() || replacement.live(),
                stage
            );
        });
    }

    private void removeStaticWorldObservation(
        TraceKey traceKey,
        BlockPosition position,
        WorldObservation.Kind kind
    ) {
        List<WorldObservation> existing = this.worldObservationChunks.get(traceKey);
        if (existing == null) return;
        ArrayList<WorldObservation> retained = new ArrayList<>(existing);
        retained.removeIf(observation -> observation.kind() == kind && observation.position().equals(position));
        if (retained.isEmpty()) this.worldObservationChunks.remove(traceKey);
        else this.worldObservationChunks.put(traceKey, List.copyOf(retained));
    }

    private void trimLiveWorldObservations() {
        trimTimedObservations(this.liveWorldObservations);
    }

    private static TimedWorldObservation strongerTimedObservation(
        TimedWorldObservation existing,
        TimedWorldObservation replacement
    ) {
        WorldObservation strongest = existing.observation().confidence() >= replacement.observation().confidence()
            ? existing.observation()
            : replacement.observation();
        return new TimedWorldObservation(
            strongest,
            Math.max(existing.expiresAtTick(), replacement.expiresAtTick())
        );
    }

    private static void trimTimedObservations(LinkedHashMap<LiveWorldKey, TimedWorldObservation> observations) {
        while (observations.size() > MAX_WORLD_OBSERVATIONS) {
            Iterator<LiveWorldKey> iterator = observations.keySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private boolean scanningEnabled() {
        return this.config.enabled || this.config.tunnelEsp || this.config.amethystEsp || this.config.accessTrailEsp;
    }

    private ChunkMarker marker(TraceKey traceKey, ChunkTrace trace) {
        int count = this.grownCounts.getOrDefault(traceKey, 0);
        int score = dev.arcaneclient.scan.GrowthCountPolicy.score(count, this.config.grownBlocksRequired);
        ScoreSummary summary = new ScoreSummary(score, 0, Map.of(SignalCategory.GROWN_PLANTS, score),
            count == 0 ? List.of() : List.of(count + " grown blocks; requires " + this.config.grownBlocksRequired));
        ChunkIntel intel = trace.intelligenceEvidenceAt(this.tick, this::allowsEvidence, true);
        List<String> reasons = summary.reasons().subList(0, Math.min(4, summary.reasons().size()));
        boolean deepAnchor = trace.legacyHasActiveEvidenceAtOrBelow(
            this.tick, this::allowsEvidence, true, 48
        );
        return new ChunkMarker(
            traceKey.x, traceKey.z, summary.score(), reasons, summary.categoryBreakdown(), intel, deepAnchor
        );
    }

    boolean allowsEvidence(ScanEvidence evidence) {
        return !evidence.isLive() && evidence.category() == SignalCategory.GROWN_PLANTS
            && evidence.family() == EvidenceFamily.GROWTH;
    }

    public int grownCountAt(int chunkX, int chunkZ) {
        return this.grownCounts.getOrDefault(this.key(chunkX, chunkZ), 0);
    }

    private boolean withinScanRadius(Minecraft client, TraceKey traceKey) {
        ChunkPos playerChunk = client.player.chunkPosition();
        return Math.abs(traceKey.x - playerChunk.x()) <= this.config.scanRadius && Math.abs(traceKey.z - playerChunk.z()) <= this.config.scanRadius;
    }

    private boolean isCurrent(TraceKey traceKey) {
        return traceKey.session.equals(this.session) && traceKey.dimension.equals(this.dimension);
    }

    private TraceKey key(int x, int z) {
        return new TraceKey(this.session, this.dimension, x, z);
    }

    private void pruneCooldowns() {
        this.eventCooldowns.entrySet().removeIf(entry -> this.tick - (Long)entry.getValue() > 9600L);
    }

    private static String sessionId(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return server == null ? "singleplayer" : server.ip.toLowerCase();
    }

    @Environment(value=EnvType.CLIENT)
    private record TraceKey(String session, String dimension, int x, int z) {
    }

    @Environment(value=EnvType.CLIENT)
    private record EventKey(BlockPosition position, SignalCategory category, String reason) {
    }

    @Environment(value=EnvType.CLIENT)
    private record ObservationIdentity(BlockPosition position, WorldObservation.Kind kind) {
    }

    @Environment(value=EnvType.CLIENT)
    private record LiveWorldKey(TraceKey traceKey, BlockPosition position, WorldObservation.Kind kind) {
    }

    @Environment(value=EnvType.CLIENT)
    private record TimedWorldObservation(WorldObservation observation, long expiresAtTick) {
    }

    @Environment(value=EnvType.CLIENT)
    private static final class DirtyChunk {
        private final Set<Integer> sections = new HashSet<Integer>();
        private long lastUpdateTick;
        private long firstUpdateTick;

        void mark(int sectionY, long tick) {
            if (this.sections.isEmpty()) this.firstUpdateTick = tick;
            this.sections.add(sectionY);
            this.lastUpdateTick = tick;
        }
    }

    @Environment(value=EnvType.CLIENT)
    public record ChunkMarker(
        int chunkX,
        int chunkZ,
        int score,
        List<String> reasons,
        Map<SignalCategory, Integer> categoryBreakdown,
        ChunkIntel intel,
        boolean deepAnchor
    ) {
        public ChunkMarker(int chunkX, int chunkZ, int score, List<String> reasons, Map<SignalCategory, Integer> categoryBreakdown) {
            this(chunkX, chunkZ, score, reasons, categoryBreakdown, ChunkIntel.EMPTY, false);
        }

        public ChunkMarker(
            int chunkX,
            int chunkZ,
            int score,
            List<String> reasons,
            Map<SignalCategory, Integer> categoryBreakdown,
            ChunkIntel intel
        ) {
            this(chunkX, chunkZ, score, reasons, categoryBreakdown, intel, false);
        }

        public ChunkMarker {
            reasons = List.copyOf(reasons);
            categoryBreakdown = Map.copyOf(new EnumMap<SignalCategory, Integer>(categoryBreakdown));
            if (intel == null) intel = ChunkIntel.EMPTY;
        }
    }

    @Environment(value=EnvType.CLIENT)
    public record ChunkTile(int chunkX, int chunkZ, int score, boolean flagged) {
    }

    @Environment(value=EnvType.CLIENT)
    private record QueuedScan(TraceKey key, boolean frontier, long sequence) {
    }

    @Environment(value=EnvType.CLIENT)
    private record ActiveScan(TraceKey key, ChunkScanner.ChunkScanJob job, boolean frontier, long sequence) {
    }

    @Environment(value=EnvType.CLIENT)
    public record ActivityClusterCandidate(
        int chunkX,
        int chunkZ,
        int confidence,
        int members,
        int families,
        int growthStrength,
        List<Long> memberChunks
    ) {
        public ActivityClusterCandidate {
            memberChunks = List.copyOf(memberChunks);
        }

        public boolean contains(int targetChunkX, int targetChunkZ) {
            return memberChunks.contains(chunkKey(targetChunkX, targetChunkZ));
        }
    }
}
