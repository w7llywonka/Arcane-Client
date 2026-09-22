package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.utility.StreamerPrivacy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One bounded cache powers Search, Portal ESP, Breadcrumbs, Logout Spots, and Chunk Borders.
 * Chunk scans are palette-gated and incremental so elytra travel never performs a world sweep.
 */
@Environment(EnvType.CLIENT)
public final class WorldIntelRenderer {
    private static final int BLOCK_BUDGET_PER_TICK = 24_576;
    private static final int MAX_SEARCH_TARGETS = 4_096;
    private static final VoxelShape BLOCK_BOX = Shapes.box(0.05, 0.05, 0.05, 0.95, 0.95, 0.95);
    private static final VoxelShape PLAYER_BOX = Shapes.box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);

    private static final ArrayDeque<SearchJob> SEARCH_QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED_CHUNKS = new HashSet<>();
    private static final Map<Long, List<SearchTarget>> SEARCH_TARGETS = new LinkedHashMap<>();
    private static final ArrayDeque<Vec3> BREADCRUMBS = new ArrayDeque<>();
    private static final Map<UUID, PlayerSnapshot> VISIBLE_PLAYERS = new HashMap<>();
    private static final Map<UUID, LogoutSpot> LOGOUT_SPOTS = new LinkedHashMap<>();
    private static ClientLevel activeWorld;
    private static int indexedMode;
    private static long lastPlayerRefresh = Long.MIN_VALUE;
    private static long lastValidation = Long.MIN_VALUE;

    private WorldIntelRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(WorldIntelRenderer::render);
    }

    public static void onWorldChange(ClientLevel world) {
        activeWorld = world;
        SEARCH_QUEUE.clear();
        QUEUED_CHUNKS.clear();
        SEARCH_TARGETS.clear();
        BREADCRUMBS.clear();
        VISIBLE_PLAYERS.clear();
        LOGOUT_SPOTS.clear();
        indexedMode = 0;
        lastPlayerRefresh = Long.MIN_VALUE;
        lastValidation = Long.MIN_VALUE;
    }

    public static void onChunkLoad(ClientLevel world, LevelChunk chunk) {
        if (world != activeWorld) onWorldChange(world);
        ArcaneConfig config = ArcaneClient.config();
        if (config != null && (config.searchEsp || config.portalEsp)) enqueue(chunk);
    }

    public static void onChunkUnload(ClientLevel world, LevelChunk chunk) {
        long key = chunk.getPos().pack();
        SEARCH_TARGETS.remove(key);
        QUEUED_CHUNKS.remove(key);
        SEARCH_QUEUE.removeIf(job -> job.chunkKey == key);
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.level == null || client.player == null) {
            if (activeWorld != null) onWorldChange(null);
            return;
        }
        if (activeWorld != client.level) onWorldChange(client.level);
        int requestedMode = (config.searchEsp ? 1 : 0) | (config.portalEsp ? 2 : 0);
        boolean searchEnabled = requestedMode != 0;
        if (requestedMode != indexedMode) {
            SEARCH_QUEUE.clear();
            QUEUED_CHUNKS.clear();
            SEARCH_TARGETS.clear();
            indexedMode = requestedMode;
            if (searchEnabled) queueLoadedChunks(client, config);
        }
        if (searchEnabled) processSearchQueue();
        else {
            SEARCH_QUEUE.clear();
            QUEUED_CHUNKS.clear();
            SEARCH_TARGETS.clear();
        }

        tickBreadcrumbs(client, config);
        long time = client.level.getGameTime();
        if (time < lastPlayerRefresh || time - lastPlayerRefresh >= 20) {
            lastPlayerRefresh = time;
            refreshPlayers(client, config);
        }
        if (time < lastValidation || time - lastValidation >= 40) {
            lastValidation = time;
            validateSearchTargets(client.level);
        }
    }

    private static void queueLoadedChunks(Minecraft client, ArcaneConfig config) {
        ChunkPos center = client.player.chunkPosition();
        int blocks = Math.max(config.searchEsp ? config.searchEspRange : 0, config.portalEsp ? config.portalEspRange : 0);
        int radius = Math.min(12, Math.max(1, (blocks + 15) / 16));
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    LevelChunk chunk = client.level.getChunkSource().getChunk(center.x() + dx, center.z() + dz, false);
                    if (chunk != null) enqueue(chunk);
                }
            }
        }
    }

    private static void enqueue(LevelChunk chunk) {
        long key = chunk.getPos().pack();
        ArcaneConfig config = ArcaneClient.config();
        if (config != null && QUEUED_CHUNKS.add(key)) SEARCH_QUEUE.addLast(new SearchJob(chunk, config.searchEsp, config.portalEsp));
    }

    private static void processSearchQueue() {
        int budget = BLOCK_BUDGET_PER_TICK;
        while (budget > 0 && !SEARCH_QUEUE.isEmpty()) {
            SearchJob job = SEARCH_QUEUE.peekFirst();
            int visited = job.step(budget);
            budget -= Math.max(1, visited);
            if (!job.complete()) break;
            SEARCH_QUEUE.removeFirst();
            QUEUED_CHUNKS.remove(job.chunkKey);
            if (!job.targets.isEmpty()) SEARCH_TARGETS.put(job.chunkKey, List.copyOf(job.targets));
            else SEARCH_TARGETS.remove(job.chunkKey);
            trimTargets();
        }
    }

    private static void trimTargets() {
        int count = SEARCH_TARGETS.values().stream().mapToInt(List::size).sum();
        Iterator<Map.Entry<Long, List<SearchTarget>>> iterator = SEARCH_TARGETS.entrySet().iterator();
        while (count > MAX_SEARCH_TARGETS && iterator.hasNext()) {
            count -= iterator.next().getValue().size();
            iterator.remove();
        }
    }

    private static void validateSearchTargets(ClientLevel world) {
        Iterator<Map.Entry<Long, List<SearchTarget>>> chunks = SEARCH_TARGETS.entrySet().iterator();
        while (chunks.hasNext()) {
            Map.Entry<Long, List<SearchTarget>> entry = chunks.next();
            ArrayList<SearchTarget> live = new ArrayList<>(entry.getValue().size());
            for (SearchTarget target : entry.getValue()) {
                WorldIntelPolicy.SearchKind actual = classify(world.getBlockState(target.pos));
                if (actual == target.kind) live.add(target);
            }
            if (live.isEmpty()) chunks.remove();
            else entry.setValue(List.copyOf(live));
        }
    }

    private static void tickBreadcrumbs(Minecraft client, ArcaneConfig config) {
        if (!config.breadcrumbs) {
            BREADCRUMBS.clear();
            return;
        }
        Vec3 current = position(client.player).add(0.0, 0.12, 0.0);
        Vec3 previous = BREADCRUMBS.peekLast();
        if (previous == null || WorldIntelPolicy.recordBreadcrumb(previous.distanceToSqr(current), false)) {
            BREADCRUMBS.addLast(current);
        }
        while (BREADCRUMBS.size() > config.breadcrumbLength) BREADCRUMBS.removeFirst();
    }

    private static void refreshPlayers(Minecraft client, ArcaneConfig config) {
        if (!config.logoutSpots) {
            VISIBLE_PLAYERS.clear();
            LOGOUT_SPOTS.clear();
            return;
        }
        Map<UUID, PlayerSnapshot> current = new HashMap<>();
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == client.player) continue;
            current.put(player.getUUID(), new PlayerSnapshot(player.getName().getString(), position(player)));
            LOGOUT_SPOTS.remove(player.getUUID());
        }
        for (Map.Entry<UUID, PlayerSnapshot> entry : VISIBLE_PLAYERS.entrySet()) {
            if (current.containsKey(entry.getKey())) continue;
            PlayerSnapshot before = entry.getValue();
            BlockPos block = BlockPos.containing(before.pos);
            ChunkPos chunk = new ChunkPos(SectionPos.blockToSectionCoord(block.getX()), SectionPos.blockToSectionCoord(block.getZ()));
            boolean loaded = client.level.getChunkSource().getChunk(chunk.x(), chunk.z(), false) != null;
            boolean stillListed = client.getConnection() != null
                && client.getConnection().getPlayerInfo(entry.getKey()) != null;
            if (!stillListed && WorldIntelPolicy.probableLogout(true, false, loaded, false, false)) {
                LOGOUT_SPOTS.put(entry.getKey(), new LogoutSpot(before.name, before.pos, System.currentTimeMillis()));
            }
        }
        VISIBLE_PLAYERS.clear();
        VISIBLE_PLAYERS.putAll(current);
        long now = System.currentTimeMillis();
        LOGOUT_SPOTS.values().removeIf(spot -> !WorldIntelPolicy.liveSpot(now, spot.createdMillis, config.logoutSpotMinutes));
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.level == null || client.player == null || context.poseStack() == null
            || ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        if (!config.searchEsp && !config.portalEsp && !config.breadcrumbs && !config.logoutSpots && !config.chunkBorders && !config.waypoints) return;

        Vec3 camera = Render263.camera(context);
        Vec3 playerPosition = position(client.player);
        double searchRangeSq = config.searchEspRange * (double)config.searchEspRange;
        double portalRangeSq = config.portalEspRange * (double)config.portalEspRange;
        Render263.Batch batch = Render263.batch(context);

        for (List<SearchTarget> chunkTargets : SEARCH_TARGETS.values()) {
            for (SearchTarget target : chunkTargets) {
                boolean portal = target.kind == WorldIntelPolicy.SearchKind.PORTAL;
                boolean enabled = portal ? config.portalEsp : config.searchEsp;
                double rangeSq = portal ? portalRangeSq : searchRangeSq;
                if (!enabled || target.pos.distToCenterSqr(playerPosition) > rangeSq) continue;
                int color = portal ? config.portalEspColor : config.searchEspColor;
                batch.outline(BLOCK_BOX, target.pos.getX(), target.pos.getY(), target.pos.getZ(), color, 1.6f, true);
            }
        }
        if (config.breadcrumbs && BREADCRUMBS.size() > 1) {
            Vec3 before = null;
            for (Vec3 point : BREADCRUMBS) {
                if (before != null) {
                    batch.line(before.x, before.y, before.z, point.x, point.y, point.z,
                        0xB8000000 | config.breadcrumbColor & 0x00FFFFFF, 1.25f, true);
                }
                before = point;
            }
        }
        if (config.chunkBorders) drawChunkBorder(client, batch, config.chunkBorderColor);
        if (config.logoutSpots) {
            for (LogoutSpot spot : LOGOUT_SPOTS.values()) {
                batch.outline(PLAYER_BOX, spot.pos.x, spot.pos.y, spot.pos.z,
                    config.logoutSpotColor, 1.8f, true);
            }
        }
        List<WaypointStore.Waypoint> waypoints = config.waypoints ? WaypointStore.current(client) : List.of();
        for (WaypointStore.Waypoint waypoint : waypoints) {
            BlockPos pos = waypoint.pos();
            batch.outline(BLOCK_BOX, pos.getX(), pos.getY(), pos.getZ(), config.waypointColor, 1.8f, true);
        }
        batch.submit();
        if (config.logoutSpots) {
            for (LogoutSpot spot : LOGOUT_SPOTS.values()) {
                String name = StreamerPrivacy.entityName(config.streamerMode, true, spot.name);
                drawLabel(context, "LOGOUT · " + name, spot.pos.add(0, 2.1, 0), config.logoutSpotColor);
            }
        }
        for (WaypointStore.Waypoint waypoint : waypoints) {
            BlockPos pos = waypoint.pos();
            int distance = (int)Math.round(Math.sqrt(pos.distToCenterSqr(playerPosition)));
            drawLabel(context, waypoint.name() + " · " + distance + "m", Vec3.atCenterOf(pos).add(0, 0.85, 0), config.waypointColor);
        }
    }

    private static void drawChunkBorder(Minecraft client, Render263.Batch batch, int color) {
        ChunkPos chunk = client.player.chunkPosition();
        double x0 = chunk.getMinBlockX();
        double x1 = x0 + 16.0;
        double z0 = chunk.getMinBlockZ();
        double z1 = z0 + 16.0;
        double y = Math.floor(client.player.getY());
        line(batch, x0, y, z0, x1, y, z0, color);
        line(batch, x1, y, z0, x1, y, z1, color);
        line(batch, x1, y, z1, x0, y, z1, color);
        line(batch, x0, y, z1, x0, y, z0, color);
        line(batch, x0, y - 16, z0, x0, y + 16, z0, color);
        line(batch, x1, y - 16, z0, x1, y + 16, z0, color);
        line(batch, x1, y - 16, z1, x1, y + 16, z1, color);
        line(batch, x0, y - 16, z1, x0, y + 16, z1, color);
    }

    private static void line(Render263.Batch batch, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        batch.line(x0, y0, z0, x1, y1, z1, color, 1.2f, true);
    }

    private static void drawLabel(LevelRenderContext context, String label, Vec3 pos, int color) {
        Minecraft client = Minecraft.getInstance();
        Font font = ArcaneFont.renderer(client);
        Render263.text(context, font, label, pos, 0.025f, color, true);
    }

    private static WorldIntelPolicy.SearchKind classify(BlockState state) {
        if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)) {
            return WorldIntelPolicy.SearchKind.PORTAL;
        }
        if (state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER) || state.is(Blocks.VAULT)
            || state.is(Blocks.BEACON) || state.is(Blocks.LODESTONE) || state.is(Blocks.RESPAWN_ANCHOR)
            || state.is(Blocks.ANCIENT_DEBRIS)) {
            return WorldIntelPolicy.SearchKind.SEARCH;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return WorldIntelPolicy.classify(id == null ? "" : id.getPath());
    }

    private static Vec3 position(net.minecraft.world.entity.Entity entity) {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }

    public static int searchTargetCount() {
        return SEARCH_TARGETS.values().stream()
            .flatMap(List::stream)
            .mapToInt(target -> target.kind == WorldIntelPolicy.SearchKind.SEARCH ? 1 : 0)
            .sum();
    }

    public static int queuedChunkCount() {
        return SEARCH_QUEUE.size();
    }

    private static final class SearchJob {
        private final LevelChunk chunk;
        private final LevelChunkSection[] sections;
        private final long chunkKey;
        private final boolean includeSearch;
        private final boolean includePortal;
        private final ArrayList<SearchTarget> targets = new ArrayList<>();
        private int sectionIndex;
        private int blockIndex;

        private SearchJob(LevelChunk chunk, boolean includeSearch, boolean includePortal) {
            this.chunk = chunk;
            this.sections = chunk.getSections();
            this.chunkKey = chunk.getPos().pack();
            this.includeSearch = includeSearch;
            this.includePortal = includePortal;
        }

        private int step(int budget) {
            int visited = 0;
            while (sectionIndex < sections.length && visited < budget) {
                LevelChunkSection section = sections[sectionIndex];
                if (section.hasOnlyAir() || blockIndex == 0 && !section.maybeHas(state -> included(classify(state)))) {
                    sectionIndex++;
                    blockIndex = 0;
                    continue;
                }
                int x = blockIndex & 15;
                int z = blockIndex >>> 4 & 15;
                int y = blockIndex >>> 8 & 15;
                WorldIntelPolicy.SearchKind kind = classify(section.getBlockState(x, y, z));
                if (included(kind) && targets.size() < 512) {
                    int worldY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex)) + y;
                    targets.add(new SearchTarget(new BlockPos(chunk.getPos().getMinBlockX() + x, worldY, chunk.getPos().getMinBlockZ() + z), kind));
                }
                blockIndex++;
                visited++;
                if (blockIndex == 4096) {
                    sectionIndex++;
                    blockIndex = 0;
                }
            }
            return visited;
        }

        private boolean complete() {
            return sectionIndex >= sections.length;
        }

        private boolean included(WorldIntelPolicy.SearchKind kind) {
            return kind == WorldIntelPolicy.SearchKind.SEARCH && includeSearch
                || kind == WorldIntelPolicy.SearchKind.PORTAL && includePortal;
        }
    }

    private record SearchTarget(BlockPos pos, WorldIntelPolicy.SearchKind kind) {
    }

    private record PlayerSnapshot(String name, Vec3 pos) {
    }

    private record LogoutSpot(String name, Vec3 pos, long createdMillis) {
    }
}
