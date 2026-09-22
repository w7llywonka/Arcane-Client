package dev.arcaneclient.additions.susfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** A separate received-chunk scanner; never calls or feeds TraceEngine or its growth-only score. */
public final class SusChunkFinderController {
    private static final int MAX_QUEUE = 192;
    private static final int MAX_RESULTS = 512;
    private static final int RESCAN_TICKS = 100;
    private static final int DISCOVERY_PER_TICK = 64;
    private static final int MAX_CHUNKS_PER_TICK = 12;
    private static final int MAX_PALETTES_PER_TICK = 192;
    private static final long SCAN_NANOS_PER_TICK = 2_000_000L;
    private static SusChunkFinderController instance;
    private final Supplier<SusChunkFinderConfig> settings;
    private final ReceivedBlockLightCache light = new ReceivedBlockLightCache(192);
    private final LinkedHashMap<Long, LevelChunk> queue = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Snapshot> snapshots = new LinkedHashMap<>();
    private ClientLevel world;
    private Scan scan;
    private int ticks, signature, discoveryCursor, centerX = Integer.MIN_VALUE, centerZ;
    private int radius;
    private int lastTickInspectedBlocks, lastTickPaletteChecks, maxChunksCompletedInTick;
    private long receivedLightPackets, acceptedLightPackets, rejectedLightPackets;
    private int lastLightChunkX, lastLightChunkZ, lastLightInitialized, lastLightEmpty, lastLightArrays;
    private int lastLightBottom, lastLightHeight;
    private String lastLightStatus = "none";
    private boolean publishedDirty;
    private List<SusChunkGeometry.Offset> offsets = List.of();
    private List<SusZoneGrouping.Candidate> candidates = List.of();
    private List<SusZoneGrouping.Zone> zones = List.of();

    public record Snapshot(int chunkX, int chunkZ, Map<SusScoring.Family, Integer> counts,
                           int inferredLightHints, int scannedTick, LevelChunk source) { }
    private record Progress(int blocks, int palettes) { }

    private SusChunkFinderController(Supplier<SusChunkFinderConfig> settings) { this.settings = settings; }

    public static void register(Supplier<SusChunkFinderConfig> settings) {
        if (instance != null) return;
        instance = new SusChunkFinderController(settings);
        ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> instance.reset());
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!settings.get().enabled) return;
            instance.bindWorld(level);
                instance.snapshots.remove(chunk.getPos().pack());
                instance.light.unload(chunk.getPos().pack());
            instance.enqueue(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
                if (instance.world == level) instance.unload(chunk.getPos().pack());
        });
        SusChunkFinderRenderer.register(instance, settings);
    }

    public static SusChunkFinderController instance() { return instance; }
    public List<SusZoneGrouping.Candidate> candidates() { return candidates; }
    public List<SusZoneGrouping.Zone> zones() { return zones; }
    public int pendingChunks() { return queue.size() + (scan == null ? 0 : 1); }
    public int scannedChunks() { return snapshots.size(); }
    public int cachedLightChunks() { return light.size(); }
    public int lastTickInspectedBlocks() { return lastTickInspectedBlocks; }
    public int lastTickPaletteChecks() { return lastTickPaletteChecks; }
    public int maxChunksCompletedInTick() { return maxChunksCompletedInTick; }
    public int receivedBlockLightAt(int x, int y, int z) { return light.sample(x, y, z); }
    /** Constant-space diagnostics; no packets, block coordinates or payload arrays are retained here. */
    public String lightDiagnostics() {
        return "received=" + receivedLightPackets + ", accepted=" + acceptedLightPackets + ", rejected=" + rejectedLightPackets
            + ", last=" + lastLightStatus + ", chunk=" + lastLightChunkX + "," + lastLightChunkZ
            + ", masks=" + lastLightInitialized + "/" + lastLightEmpty + ", arrays=" + lastLightArrays
            + ", bottomSection=" + lastLightBottom + ", height=" + lastLightHeight + ", cached=" + light.size();
    }
    public Snapshot snapshotAt(int chunkX, int chunkZ) { return snapshots.get(ChunkPos.pack(chunkX, chunkZ)); }

    public void reset() {
        world = null; scan = null; ticks = 0; signature = 0; centerX = Integer.MIN_VALUE;
        discoveryCursor = 0; offsets = List.of(); queue.clear(); snapshots.clear(); light.clear();
        candidates = List.of(); zones = List.of(); publishedDirty = false;
        lastTickInspectedBlocks = lastTickPaletteChecks = maxChunksCompletedInTick = 0;
        receivedLightPackets = acceptedLightPackets = rejectedLightPackets = 0;
        lastLightChunkX = lastLightChunkZ = lastLightInitialized = lastLightEmpty = lastLightArrays = lastLightBottom = lastLightHeight = 0;
        lastLightStatus = "none";
    }

    private void bindWorld(ClientLevel current) {
        if (world != current) { reset(); world = current; light.bindWorld(current); }
    }

    private void unload(long key) {
        queue.remove(key); snapshots.remove(key); light.unload(key);
        if (scan != null && scan.chunk.getPos().pack() == key) scan = null;
        publishedDirty = true;
        // Remove visible stale markers synchronously with the unload event.
        candidates = candidates.stream().filter(candidate -> candidate.key() != key).toList();
        zones = SusZoneGrouping.group(candidates, settings.get().mergeRadius);
    }

    /** Called only from vanilla's readLightData, after its packet dispatch has reached the main thread. */
    public static void receive(ClientLevel packetWorld, int x, int z, ClientboundLightUpdatePacketData data) {
        if (instance == null) return;
        Minecraft client = Minecraft.getInstance();
        SusChunkFinderConfig config = instance.settings.get();
        if (!client.isSameThread() || client.level != packetWorld) return;
        if (!config.enabled || !config.amethyst || !config.inferredAmethystLight) {
            instance.lastLightStatus = "disabled";
            return;
        }
        instance.bindWorld(packetWorld);
        instance.receivedLightPackets++;
        instance.lastLightChunkX = x; instance.lastLightChunkZ = z;
        instance.lastLightInitialized = data.blockYMask().cardinality();
        instance.lastLightEmpty = data.emptyBlockYMask().cardinality();
        instance.lastLightArrays = data.blockUpdates().size();
        LevelChunk chunk = packetWorld.getChunkSource().getChunk(x, z, false);
        if (chunk == null) { instance.rejectLight("unloaded"); return; }
        if (!instance.valid(chunk)) { instance.rejectLight("out of range"); return; }
        var lighting = packetWorld.getChunkSource().getLightEngine();
        instance.lastLightBottom = lighting.getMinLightSection(); instance.lastLightHeight = lighting.getLightSectionCount();
        if (instance.light.apply(chunk.getPos().pack(), lighting.getMinLightSection(), lighting.getLightSectionCount(),
            data.blockYMask(), data.emptyBlockYMask(), data.blockUpdates())) {
            instance.acceptedLightPackets++;
            instance.lastLightStatus = "accepted";
            // A completed scan with old light must refresh even if the ordinary rescan interval has not elapsed.
            instance.queue.put(chunk.getPos().pack(), chunk);
            if (instance.scan != null && instance.scan.chunk == chunk) instance.scan.dirty = true;
            instance.trimQueue();
        } else instance.rejectLight("invalid mask/payload");
    }

    private void rejectLight(String reason) { rejectedLightPackets++; lastLightStatus = reason; }

    public static void blocksChanged(ClientLevel packetWorld, int chunkX, int chunkZ) {
        if (instance == null || !instance.settings.get().enabled) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level != packetWorld || instance.world != packetWorld) return;
        LevelChunk chunk = packetWorld.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null || !instance.valid(chunk)) return;
        long key = chunk.getPos().pack();
        instance.snapshots.remove(key);
        instance.queue.put(key, chunk);
        if (instance.scan != null && instance.scan.chunk == chunk) instance.scan.dirty = true;
        instance.trimQueue();
        instance.publishedDirty = true;
    }

    private void tick(Minecraft client) {
        SusChunkFinderConfig config = settings.get();
        if (client.level == null || client.player == null || !config.enabled) {
            if (world != null) reset();
            return;
        }
        bindWorld(client.level);
        ticks++;
        int currentSignature = config.scanSignature();
        if (signature != currentSignature) {
            queue.clear(); snapshots.clear(); scan = null; candidates = List.of(); zones = List.of();
            if (!config.amethyst || !config.inferredAmethystLight) light.clear();
            signature = currentSignature; centerX = Integer.MIN_VALUE;
        }
        if (centerX != client.player.chunkPosition().x() || centerZ != client.player.chunkPosition().z()) {
            boolean distantMove = centerX == Integer.MIN_VALUE
                || Math.abs((long)centerX - client.player.chunkPosition().x()) > radius
                || Math.abs((long)centerZ - client.player.chunkPosition().z()) > radius;
            centerX = client.player.chunkPosition().x(); centerZ = client.player.chunkPosition().z();
            // Continue the sweep during ordinary flight so its outer rings receive service too.
            if (distantMove) discoveryCursor = 0;
            int newRadius = SusChunkGeometry.discoveryRadius(config.scanRange);
            if (radius != newRadius || offsets.isEmpty()) {
                radius = newRadius;
                offsets = SusChunkGeometry.discoveryOffsets(config.scanRange);
            }
            prune();
        }
        for (int i = 0; i < DISCOVERY_PER_TICK && !offsets.isEmpty(); i++) {
            SusChunkGeometry.Offset offset = offsets.get(discoveryCursor);
            discoveryCursor = (discoveryCursor + 1) % offsets.size();
            LevelChunk loaded = world.getChunkSource().getChunk(centerX + offset.x(), centerZ + offset.z(), false);
            if (loaded != null) enqueue(loaded);
        }
        if (ticks % 10 == 0) prune();
        if (scan != null && !valid(scan.chunk)) scan = null;
        int blocksLeft = Math.clamp(config.scanBudget, 512, 16384), palettesLeft = MAX_PALETTES_PER_TICK;
        int completed = 0, attempts = 0;
        long deadline = System.nanoTime() + SCAN_NANOS_PER_TICK;
        lastTickInspectedBlocks = lastTickPaletteChecks = 0;
        while (attempts++ < MAX_CHUNKS_PER_TICK && blocksLeft > 0 && palettesLeft > 0 && System.nanoTime() < deadline) {
            if (scan == null && !queue.isEmpty()) {
                Map.Entry<Long, LevelChunk> nearest = queue.entrySet().stream()
                    .min(Comparator.<Map.Entry<Long, LevelChunk>>comparingInt(entry -> snapshots.containsKey(entry.getKey()) ? 1 : 0)
                        .thenComparingDouble(entry -> distance(entry.getValue()))).orElseThrow();
                LevelChunk chunk = nearest.getValue(); queue.remove(nearest.getKey());
                if (valid(chunk)) scan = new Scan(chunk, config);
            }
            if (scan == null) { if (queue.isEmpty()) break; else continue; }
            Progress progress = scan.step(blocksLeft, palettesLeft, deadline);
            blocksLeft -= progress.blocks; palettesLeft -= progress.palettes;
            lastTickInspectedBlocks += progress.blocks; lastTickPaletteChecks += progress.palettes;
            if (scan.complete()) {
                if (!scan.dirty) snapshots.put(scan.chunk.getPos().pack(), scan.finish());
                scan = null;
                while (snapshots.size() > MAX_RESULTS) snapshots.remove(snapshots.keySet().iterator().next());
                publishedDirty = true;
                completed++;
            } else break;
        }
        maxChunksCompletedInTick = Math.max(maxChunksCompletedInTick, completed);
        if (publishedDirty || ticks % 10 == 0) publish(config);
    }

    private void enqueue(LevelChunk chunk) {
        long key = chunk.getPos().pack();
        if (queue.containsKey(key) || scan != null && scan.chunk == chunk || !valid(chunk)) return;
        Snapshot previous = snapshots.get(key);
        if (previous != null && previous.source == chunk && ticks - previous.scannedTick < RESCAN_TICKS) return;
        queue.put(key, chunk); trimQueue();
    }

    private void trimQueue() {
        while (queue.size() > MAX_QUEUE) {
            Long farthest = queue.entrySet().stream().max(Comparator.comparingDouble(entry -> distance(entry.getValue())))
                .orElseThrow().getKey();
            queue.remove(farthest);
        }
    }

    private double distance(LevelChunk chunk) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return Double.MAX_VALUE;
        return SusChunkGeometry.distanceSquared(client.player.getX(), client.player.getZ(), chunk.getPos().x(), chunk.getPos().z());
    }

    private boolean valid(LevelChunk chunk) {
        return world != null && chunk.getLevel() == world
            && world.getChunkSource().getChunk(chunk.getPos().x(), chunk.getPos().z(), false) == chunk
            && distance(chunk) <= (double)settings.get().scanRange * settings.get().scanRange;
    }

    private void prune() {
        queue.values().removeIf(chunk -> !valid(chunk));
        if (snapshots.values().removeIf(snapshot -> !valid(snapshot.source))) publishedDirty = true;
        light.retainLoaded(key -> world.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false) != null);
    }

    private void publish(SusChunkFinderConfig config) {
        var next = new ArrayList<SusZoneGrouping.Candidate>();
        for (Snapshot snapshot : snapshots.values()) {
            SusScoring.Score score = SusScoring.score(snapshot.counts, snapshot.inferredLightHints, config);
            if (score.value() >= config.threshold) next.add(new SusZoneGrouping.Candidate(
                snapshot.chunkX, snapshot.chunkZ, score.value(), score.inferred()));
        }
        candidates = List.copyOf(next);
        zones = SusZoneGrouping.group(candidates, config.mergeRadius);
        publishedDirty = false;
    }

    private final class Scan {
        final LevelChunk chunk;
        final SusChunkFinderConfig config;
        final EnumMap<SusScoring.Family, Integer> counts = new EnumMap<>(SusScoring.Family.class);
        final Set<Long> hintCells = new HashSet<>();
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int sectionIndex, blockIndex;
        boolean prepared, dirty;

        Scan(LevelChunk chunk, SusChunkFinderConfig config) { this.chunk = chunk; this.config = config; }
        boolean complete() { return sectionIndex == chunk.getSections().length; }

        Progress step(int budget, int paletteBudget, long deadline) {
            int inspected = 0, palettes = 0;
            var sections = chunk.getSections();
            while (sectionIndex < sections.length && inspected < budget && palettes < paletteBudget) {
                // Cooperative time bound checked between palettes and every 128 positions.
                if ((inspected & 127) == 0 && System.nanoTime() >= deadline) break;
                var section = sections[sectionIndex];
                if (!prepared) {
                    palettes++;
                    if (section.hasOnlyAir() || !section.maybeHas(this::relevant)) { sectionIndex++; continue; }
                    prepared = true;
                }
                int x = blockIndex & 15, z = blockIndex >>> 4 & 15, y = blockIndex >>> 8;
                BlockState state = section.getBlockState(x, y, z);
                SusScoring.Family family = SusBlockSignals.family(state);
                if (family != null && config.enabled(family)) counts.merge(family, 1, Integer::sum);
                if (config.amethyst && config.inferredAmethystLight && hintCells.size() < 6 && SusBlockSignals.geodeShell(state)) {
                    inspectShell(state, chunk.getPos().getMinBlockX() + x, chunk.getSectionYFromSectionIndex(sectionIndex) * 16 + y,
                        chunk.getPos().getMinBlockZ() + z);
                }
                inspected++;
                if (++blockIndex == 4096) { sectionIndex++; blockIndex = 0; prepared = false; }
            }
            return new Progress(inspected, palettes);
        }

        private boolean relevant(BlockState state) {
            SusScoring.Family family = SusBlockSignals.family(state);
            return family != null && config.enabled(family)
                || config.amethyst && config.inferredAmethystLight && SusBlockSignals.geodeShell(state);
        }

        private void inspectShell(BlockState shell, int x, int y, int z) {
            // Calcite by itself also occurs in mountains. Require received neighbouring smooth basalt
            // or amethyst before using it as geode context.
            if (shell.is(Blocks.CALCITE) && !geodeNeighbour(x - 1, y, z) && !geodeNeighbour(x + 1, y, z)
                && !geodeNeighbour(x, y - 1, z) && !geodeNeighbour(x, y + 1, z)
                && !geodeNeighbour(x, y, z - 1) && !geodeNeighbour(x, y, z + 1)) return;
            // Probe six neighbours of a received shell. A 4-block cell deduplicates a light contour;
            // no position is published as a hidden cluster, and propagated light is only a weak hint.
            probe(x - 1, y, z); probe(x + 1, y, z); probe(x, y - 1, z);
            probe(x, y + 1, z); probe(x, y, z - 1); probe(x, y, z + 1);
        }

        private boolean geodeNeighbour(int x, int y, int z) {
            LevelChunk received = world.getChunkSource().getChunk(x >> 4, z >> 4, false);
            if (received == null || y < world.getMinY() || y > world.getMaxY()) return false;
            BlockState state = received.getBlockState(mutable.set(x, y, z));
            return state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
        }

        private void probe(int x, int y, int z) {
            if (hintCells.size() >= 6 || light.sample(x, y, z) != 5) return;
            LevelChunk received = world.getChunkSource().getChunk(x >> 4, z >> 4, false);
            if (received == null || y < world.getMinY() || y > world.getMaxY()) return;
            BlockState state = received.getBlockState(mutable.set(x, y, z));
            // Visible emitters and observed buds already have a direct explanation. Only concealed or
            // non-emissive received cells qualify, and even then the text always calls it inferred.
            if (state.hasBlockEntity() || state.getLightEmission() > 0 || SusBlockSignals.family(state) == SusScoring.Family.AMETHYST) return;
            hintCells.add(BlockPos.asLong(x >> 2, y >> 2, z >> 2));
        }

        Snapshot finish() {
            return new Snapshot(chunk.getPos().x(), chunk.getPos().z(), Map.copyOf(counts), hintCells.size(), ticks, chunk);
        }
    }
}
