package dev.arcaneclient;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ChunkTrace;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.ScoreSummary;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.model.StashHeuristics;
import dev.arcaneclient.model.TunnelSegment;
import dev.arcaneclient.scan.ChunkScanner;
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
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;
import org.jspecify.annotations.Nullable;

@Environment(value=EnvType.CLIENT)
public final class TraceEngine {
    private static final int MAX_REMEMBERED_CHUNKS = 12000;
    private static final int LIVE_DECAY_TICKS = 9600;
    private static final int SCAN_SLICE_BLOCKS = 2048;
    private static final int MAX_SCAN_SLICES_PER_TICK = 128;
    private static final int MAX_RENDERED_TUNNELS = 1500;
    private static final int MAX_STASH_LABELS = 24;
    private final ArcaneConfig config;
    private final ChunkScanner scanner = new ChunkScanner();
    private final LinkedHashMap<TraceKey, ChunkTrace> traces = new LinkedHashMap<>();
    private final ArrayDeque<TraceKey> scanQueue = new ArrayDeque<>();
    private final Set<TraceKey> queued = new HashSet<TraceKey>();
    private final ArrayDeque<ActiveScan> activeScans = new ArrayDeque<>();
    private final Set<TraceKey> activeScanKeys = new HashSet<TraceKey>();
    private final Map<TraceKey, Integer> scannedChunks = new HashMap<TraceKey, Integer>();
    private final Map<EventKey, Long> eventCooldowns = new HashMap<EventKey, Long>();
    private final Map<TraceKey, List<TunnelSegment>> tunnelSegments = new HashMap<TraceKey, List<TunnelSegment>>();
    private long tick;
    private String session = "menu";
    private String dimension = "unknown";
    private @Nullable ClientWorld level;
    private volatile List<ChunkMarker> markerSnapshot = List.of();
    private volatile List<TunnelSegment> tunnelSnapshot = List.of();
    private volatile List<StashCandidate> stashSnapshot = List.of();

    public TraceEngine(ArcaneConfig config) {
        this.config = config;
    }

    public void onWorldChange(MinecraftClient client, @Nullable ClientWorld newLevel) {
        this.level = newLevel;
        this.scanner.resetTemporalHistory();
        this.scanQueue.clear();
        this.queued.clear();
        this.activeScans.clear();
        this.activeScanKeys.clear();
        this.scannedChunks.clear();
        this.eventCooldowns.clear();
        this.tunnelSegments.clear();
        this.markerSnapshot = List.of();
        this.tunnelSnapshot = List.of();
        this.stashSnapshot = List.of();
        if (newLevel == null) {
            this.session = "menu";
            this.dimension = "unknown";
            return;
        }
        this.session = TraceEngine.sessionId(client);
        this.dimension = newLevel.getRegistryKey().getValue().toString();
        this.queueNearby(client);
    }

    public void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        if (world == this.level && (this.config.enabled || this.config.tunnelEsp)) {
            this.enqueue(chunk.getPos().x, chunk.getPos().z);
        }
    }

    public void onChunkUnload(ClientWorld world, WorldChunk chunk) {
        if (world == this.level) {
            TraceKey traceKey = this.key(chunk.getPos().x, chunk.getPos().z);
            this.queued.remove(traceKey);
            this.scanQueue.remove(traceKey);
            this.activeScanKeys.remove(traceKey);
            this.activeScans.removeIf(scan -> scan.key.equals(traceKey));
            this.scannedChunks.remove(traceKey);
            this.tunnelSegments.remove(traceKey);
        }
    }

    public void tick(MinecraftClient client) {
        ++this.tick;
        if (client.world != this.level) {
            this.onWorldChange(client, client.world);
        }
        if (!this.config.enabled && !this.config.tunnelEsp || client.world == null || client.player == null) {
            return;
        }
        if (this.tick % ((long)this.config.rescanSeconds * 20L) == 0L) {
            this.queueNearby(client);
        }
        this.startScanJobs(client);
        this.processScanJobs(client);
        if (this.tick % 10L == 0L) {
            this.refreshMarkers(client);
            this.refreshTunnelSnapshot(client);
        }
        if (this.tick % 1200L == 0L) {
            this.pruneCooldowns();
        }
    }

    public void queueNearby(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }
        ChunkPos center = client.player.getChunkPos();
        for (int radius = 0; radius <= this.config.scanRadius; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    this.enqueue(center.x + dx, center.z + dz);
                }
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
        this.tunnelSegments.clear();
        this.scanner.resetTemporalHistory();
        this.markerSnapshot = List.of();
        this.tunnelSnapshot = List.of();
        this.stashSnapshot = List.of();
    }

    public void recordLive(BlockPos pos, SignalCategory category, int strength, String reason) {
        this.recordLive(pos, category, strength, reason, 9600, 10);
    }

    public void recordLive(BlockPos pos, SignalCategory category, int strength, String reason, int decayTicks, int cooldownTicks) {
        if (!this.config.enabled || !this.config.packetSignals || this.level == null || strength <= 0) {
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

    public void mergeStatic(int chunkX, int chunkZ, ScanResult result) {
        if (this.config.enabled && this.level != null) {
            this.merge(this.key(chunkX, chunkZ), result);
        }
    }

    public long currentTick() {
        return this.tick;
    }

    public int queueSize() {
        return this.scanQueue.size() + this.activeScans.size();
    }

    public int flaggedCount() {
        return this.markerSnapshot.size();
    }

    public boolean hasScanBaseline(int chunkX, int chunkZ, int observerSectionY) {
        return this.level != null && this.scannedChunks.getOrDefault(this.key(chunkX, chunkZ), Integer.MIN_VALUE) == observerSectionY;
    }

    public List<ChunkMarker> markers() {
        return this.markerSnapshot;
    }

    public List<TunnelSegment> tunnels() {
        return this.tunnelSnapshot;
    }

    public List<StashCandidate> stashCandidates() {
        return this.stashSnapshot;
    }

    public void settingsChanged(MinecraftClient client) {
        if (this.level != null && client.player != null) {
            this.refreshMarkers(client);
            this.refreshTunnelSnapshot(client);
        }
    }

    public void tunnelSettingsChanged(MinecraftClient client) {
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
        ArrayList<ChunkMarker> nearby = new ArrayList<ChunkMarker>();
        for (Map.Entry<TraceKey, ChunkTrace> entry : this.traces.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey) || Math.abs(traceKey.x - centerX) > radius || Math.abs(traceKey.z - centerZ) > radius) continue;
            nearby.add(this.marker(traceKey, entry.getValue()));
        }
        return List.copyOf(nearby);
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

    private void enqueue(int x, int z) {
        TraceKey traceKey = this.key(x, z);
        if (!this.activeScanKeys.contains(traceKey) && this.queued.add(traceKey)) {
            this.scanQueue.addLast(traceKey);
        }
    }

    private void startScanJobs(MinecraftClient client) {
        int targetActive = Math.max(2, this.config.chunksPerTick * 2);
        while (this.activeScans.size() < targetActive) {
            WorldChunk chunk;
            TraceKey next = this.scanQueue.pollFirst();
            if (next == null) {
                return;
            }
            this.queued.remove(next);
            if (!this.isCurrent(next) || !this.withinScanRadius(client, next) || (chunk = client.world.getChunkManager().getWorldChunk(next.x, next.z, false)) == null) continue;
            try {
                int observerSectionY = ChunkSectionPos.getSectionCoord((int)client.player.getBlockPos().getY());
                this.activeScans.addLast(new ActiveScan(next, this.scanner.begin(client.world, chunk, this.tick, observerSectionY, this.config.tunnelEsp)));
                this.activeScanKeys.add(next);
            }
            catch (RuntimeException exception) {
                ArcaneClient.LOGGER.warn("Could not start chunk scan at {}, {}", new Object[]{next.x, next.z, exception});
            }
        }
    }

    private void processScanJobs(MinecraftClient client) {
        long started = System.nanoTime();
        long deadline = started + this.scanBudgetNanos();
        int slices = 0;
        while (!(this.activeScans.isEmpty() || slices >= 128 || slices > 0 && System.nanoTime() >= deadline)) {
            ActiveScan active = this.activeScans.removeFirst();
            if (!this.isCurrent(active.key) || !this.withinScanRadius(client, active.key)) {
                this.activeScanKeys.remove(active.key);
                continue;
            }
            try {
                active.job.step(2048);
                if (active.job.isComplete()) {
                    this.activeScanKeys.remove(active.key);
                    this.scannedChunks.put(active.key, active.job.observerSectionY());
                    if (this.config.tunnelEsp) {
                        List<TunnelSegment> found = active.job.tunnels();
                        if (found.isEmpty()) {
                            this.tunnelSegments.remove(active.key);
                        } else {
                            this.tunnelSegments.put(active.key, found);
                        }
                    }
                    this.merge(active.key, active.job.result());
                } else {
                    this.activeScans.addLast(active);
                }
            }
            catch (RuntimeException exception) {
                this.activeScanKeys.remove(active.key);
                ArcaneClient.LOGGER.warn("Chunk scan failed at {}, {}", new Object[]{active.key.x, active.key.z, exception});
            }
            ++slices;
        }
    }

    private long scanBudgetNanos() {
        return Math.min(5000000L, 1000000L + (long)this.config.chunksPerTick * 500000L);
    }

    private void merge(TraceKey traceKey, ScanResult result) {
        if (result.evidence().isEmpty()) {
            return;
        }
        this.traces.computeIfAbsent(traceKey, ignored -> new ChunkTrace()).merge(result);
        while (this.traces.size() > 12000) {
            Iterator<TraceKey> iterator = this.traces.keySet().iterator();
            if (!iterator.hasNext()) continue;
            iterator.next();
            iterator.remove();
        }
    }

    private void refreshMarkers(MinecraftClient client) {
        if (!this.config.enabled) {
            this.markerSnapshot = List.of();
            this.stashSnapshot = List.of();
            return;
        }
        if (client.player == null) {
            this.markerSnapshot = List.of();
            this.stashSnapshot = List.of();
            return;
        }
        ChunkPos center = client.player.getChunkPos();
        int renderRadius = Math.max(32, this.config.scanRadius + 4);
        ArrayList<ChunkMarker> markers = new ArrayList<ChunkMarker>();
        ArrayList<ChunkMarker> scored = new ArrayList<ChunkMarker>();
        for (Map.Entry<TraceKey, ChunkTrace> entry : this.traces.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey) || Math.abs(traceKey.x - center.x) > renderRadius || Math.abs(traceKey.z - center.z) > renderRadius) continue;
            ChunkMarker marker = this.marker(traceKey, entry.getValue());
            if (marker.score > 0) {
                scored.add(marker);
            }
            if (marker.score < this.config.threshold) continue;
            markers.add(marker);
        }
        markers.sort(Comparator.comparingInt(ChunkMarker::score).reversed());
        scored.sort(Comparator.comparingInt(ChunkMarker::score).reversed());
        this.markerSnapshot = List.copyOf(markers);
        this.refreshStashSnapshot(scored);
    }

    private void refreshStashSnapshot(List<ChunkMarker> scored) {
        ArrayList<StashCandidate> candidates = new ArrayList<StashCandidate>();
        for (ChunkMarker marker : scored) {
            boolean clustered;
            boolean direct = StashHeuristics.direct(marker.score, marker.deepAnchor, marker.categoryBreakdown);
            boolean bl = clustered = !direct && StashHeuristics.clusterMember(marker.score, marker.deepAnchor, marker.categoryBreakdown) && TraceEngine.hasStrongNeighbor(marker, scored);
            if (!direct && !clustered || TraceEngine.nearExistingLabel(marker, candidates)) continue;
            candidates.add(new StashCandidate(marker.chunkX, marker.chunkZ, marker.score));
            if (candidates.size() != 24) continue;
            break;
        }
        this.stashSnapshot = List.copyOf(candidates);
    }

    private static boolean hasStrongNeighbor(ChunkMarker marker, List<ChunkMarker> scored) {
        for (ChunkMarker neighbor : scored) {
            if (neighbor == marker || Math.abs(marker.chunkX - neighbor.chunkX) > 2 || Math.abs(marker.chunkZ - neighbor.chunkZ) > 2 || !StashHeuristics.clusterPair(marker.score, marker.deepAnchor, marker.categoryBreakdown, neighbor.score, neighbor.deepAnchor, neighbor.categoryBreakdown)) continue;
            return true;
        }
        return false;
    }

    private static boolean nearExistingLabel(ChunkMarker marker, List<StashCandidate> candidates) {
        return candidates.stream().anyMatch(candidate -> Math.abs(marker.chunkX - candidate.chunkX) <= 2 && Math.abs(marker.chunkZ - candidate.chunkZ) <= 2);
    }

    private void refreshTunnelSnapshot(MinecraftClient client) {
        if (!this.config.tunnelEsp || client.player == null) {
            this.tunnelSnapshot = List.of();
            return;
        }
        ChunkPos center = client.player.getChunkPos();
        ArrayList<TunnelSegment> visible = new ArrayList<TunnelSegment>();
        for (Map.Entry<TraceKey, List<TunnelSegment>> entry : this.tunnelSegments.entrySet()) {
            TraceKey traceKey = entry.getKey();
            if (!this.isCurrent(traceKey) || Math.abs(traceKey.x - center.x) > this.config.scanRadius || Math.abs(traceKey.z - center.z) > this.config.scanRadius) continue;
            for (TunnelSegment segment : entry.getValue()) {
                visible.add(segment);
                if (visible.size() != 1500) continue;
                this.tunnelSnapshot = List.copyOf(visible);
                return;
            }
        }
        this.tunnelSnapshot = List.copyOf(visible);
    }

    private ChunkMarker marker(TraceKey traceKey, ChunkTrace trace) {
        ScoreSummary summary = trace.summarizeAt(this.tick, this.config::allows, this.config.packetSignals, this.config.deepFocus);
        List<String> reasons = summary.reasons().subList(0, Math.min(4, summary.reasons().size()));
        boolean deepAnchor = trace.hasActiveEvidenceAtOrBelow(this.tick, this.config::allows, this.config.packetSignals, 48);
        return new ChunkMarker(traceKey.x, traceKey.z, summary.score(), deepAnchor, reasons, summary.categoryBreakdown());
    }

    private boolean withinScanRadius(MinecraftClient client, TraceKey traceKey) {
        ChunkPos playerChunk = client.player.getChunkPos();
        return Math.abs(traceKey.x - playerChunk.x) <= this.config.scanRadius && Math.abs(traceKey.z - playerChunk.z) <= this.config.scanRadius;
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

    private static String sessionId(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        return server == null ? "singleplayer" : server.address.toLowerCase();
    }

    @Environment(value=EnvType.CLIENT)
    private record TraceKey(String session, String dimension, int x, int z) {
    }

    @Environment(value=EnvType.CLIENT)
    private record EventKey(BlockPosition position, SignalCategory category, String reason) {
    }

    @Environment(value=EnvType.CLIENT)
    public record ChunkMarker(int chunkX, int chunkZ, int score, boolean deepAnchor, List<String> reasons, Map<SignalCategory, Integer> categoryBreakdown) {
        public ChunkMarker {
            reasons = List.copyOf(reasons);
            categoryBreakdown = Map.copyOf(new EnumMap<SignalCategory, Integer>(categoryBreakdown));
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record ActiveScan(TraceKey key, ChunkScanner.ChunkScanJob job) {
    }

    @Environment(value=EnvType.CLIENT)
    public record StashCandidate(int chunkX, int chunkZ, int score) {
    }
}
