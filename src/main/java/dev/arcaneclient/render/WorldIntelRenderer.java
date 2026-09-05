package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
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
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Quaternionfc;

/**
 * One bounded cache powers Search, Portal ESP, Breadcrumbs, Logout Spots, and Chunk Borders.
 * Chunk scans are palette-gated and incremental so elytra travel never performs a world sweep.
 */
@Environment(EnvType.CLIENT)
public final class WorldIntelRenderer {
    private static final int BLOCK_BUDGET_PER_TICK = 24_576;
    private static final int MAX_SEARCH_TARGETS = 4_096;
    private static final VoxelShape BLOCK_BOX = VoxelShapes.cuboid(0.05, 0.05, 0.05, 0.95, 0.95, 0.95);
    private static final VoxelShape PLAYER_BOX = VoxelShapes.cuboid(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
    private static final RenderPipeline LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(ArcaneClient.id("pipeline/world_intel"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    );
    private static final RenderLayer LINE_TYPE = RenderLayer.of("arcaneclient_world_intel", RenderSetup.builder(LINES).build());

    private static final ArrayDeque<SearchJob> SEARCH_QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED_CHUNKS = new HashSet<>();
    private static final Map<Long, List<SearchTarget>> SEARCH_TARGETS = new LinkedHashMap<>();
    private static final ArrayDeque<Vec3d> BREADCRUMBS = new ArrayDeque<>();
    private static final Map<UUID, PlayerSnapshot> VISIBLE_PLAYERS = new HashMap<>();
    private static final Map<UUID, LogoutSpot> LOGOUT_SPOTS = new LinkedHashMap<>();
    private static ClientWorld activeWorld;
    private static int indexedMode;
    private static long lastPlayerRefresh = Long.MIN_VALUE;
    private static long lastValidation = Long.MIN_VALUE;

    private WorldIntelRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(WorldIntelRenderer::render);
    }

    public static void onWorldChange(ClientWorld world) {
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

    public static void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        if (world != activeWorld) onWorldChange(world);
        ArcaneConfig config = ArcaneClient.config();
        if (config != null && (config.searchEsp || config.portalEsp)) enqueue(chunk);
    }

    public static void onChunkUnload(ClientWorld world, WorldChunk chunk) {
        long key = chunk.getPos().toLong();
        SEARCH_TARGETS.remove(key);
        QUEUED_CHUNKS.remove(key);
        SEARCH_QUEUE.removeIf(job -> job.chunkKey == key);
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.world == null || client.player == null) {
            if (activeWorld != null) onWorldChange(null);
            return;
        }
        if (activeWorld != client.world) onWorldChange(client.world);
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
        long time = client.world.getTime();
        if (time < lastPlayerRefresh || time - lastPlayerRefresh >= 20) {
            lastPlayerRefresh = time;
            refreshPlayers(client, config);
        }
        if (time < lastValidation || time - lastValidation >= 40) {
            lastValidation = time;
            validateSearchTargets(client.world);
        }
    }

    private static void queueLoadedChunks(MinecraftClient client, ArcaneConfig config) {
        ChunkPos center = client.player.getChunkPos();
        int blocks = Math.max(config.searchEsp ? config.searchEspRange : 0, config.portalEsp ? config.portalEspRange : 0);
        int radius = Math.min(12, Math.max(1, (blocks + 15) / 16));
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    WorldChunk chunk = client.world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz, false);
                    if (chunk != null) enqueue(chunk);
                }
            }
        }
    }

    private static void enqueue(WorldChunk chunk) {
        long key = chunk.getPos().toLong();
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

    private static void validateSearchTargets(ClientWorld world) {
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

    private static void tickBreadcrumbs(MinecraftClient client, ArcaneConfig config) {
        if (!config.breadcrumbs) {
            BREADCRUMBS.clear();
            return;
        }
        Vec3d current = position(client.player).add(0.0, 0.12, 0.0);
        Vec3d previous = BREADCRUMBS.peekLast();
        if (previous == null || WorldIntelPolicy.recordBreadcrumb(previous.squaredDistanceTo(current), false)) {
            BREADCRUMBS.addLast(current);
        }
        while (BREADCRUMBS.size() > config.breadcrumbLength) BREADCRUMBS.removeFirst();
    }

    private static void refreshPlayers(MinecraftClient client, ArcaneConfig config) {
        if (!config.logoutSpots) {
            VISIBLE_PLAYERS.clear();
            LOGOUT_SPOTS.clear();
            return;
        }
        Map<UUID, PlayerSnapshot> current = new HashMap<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            current.put(player.getUuid(), new PlayerSnapshot(player.getName().getString(), position(player)));
            LOGOUT_SPOTS.remove(player.getUuid());
        }
        for (Map.Entry<UUID, PlayerSnapshot> entry : VISIBLE_PLAYERS.entrySet()) {
            if (current.containsKey(entry.getKey())) continue;
            PlayerSnapshot before = entry.getValue();
            ChunkPos chunk = new ChunkPos(BlockPos.ofFloored(before.pos));
            boolean loaded = client.world.getChunkManager().getWorldChunk(chunk.x, chunk.z, false) != null;
            boolean stillListed = client.getNetworkHandler() != null
                && client.getNetworkHandler().getPlayerListEntry(entry.getKey()) != null;
            if (!stillListed && WorldIntelPolicy.probableLogout(true, false, loaded, false, false)) {
                LOGOUT_SPOTS.put(entry.getKey(), new LogoutSpot(before.name, before.pos, System.currentTimeMillis()));
            }
        }
        VISIBLE_PLAYERS.clear();
        VISIBLE_PLAYERS.putAll(current);
        long now = System.currentTimeMillis();
        LOGOUT_SPOTS.values().removeIf(spot -> !WorldIntelPolicy.liveSpot(now, spot.createdMillis, config.logoutSpotMinutes));
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.world == null || client.player == null || context.matrices() == null
            || ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        if (!config.searchEsp && !config.portalEsp && !config.breadcrumbs && !config.logoutSpots && !config.chunkBorders && !config.waypoints) return;

        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = context.consumers().getBuffer(LINE_TYPE);
        double searchRangeSq = config.searchEspRange * (double)config.searchEspRange;
        double portalRangeSq = config.portalEspRange * (double)config.portalEspRange;

        for (List<SearchTarget> chunkTargets : SEARCH_TARGETS.values()) {
            for (SearchTarget target : chunkTargets) {
                boolean portal = target.kind == WorldIntelPolicy.SearchKind.PORTAL;
                boolean enabled = portal ? config.portalEsp : config.searchEsp;
                double rangeSq = portal ? portalRangeSq : searchRangeSq;
                if (!enabled || target.pos.getSquaredDistance(position(client.player)) > rangeSq) continue;
                int color = portal ? config.portalEspColor : config.searchEspColor;
                VertexRendering.drawOutline(
                    matrices,
                    lines,
                    BLOCK_BOX,
                    target.pos.getX() - camera.x,
                    target.pos.getY() - camera.y,
                    target.pos.getZ() - camera.z,
                    color,
                    1.6f
                );
            }
        }
        if (config.breadcrumbs && BREADCRUMBS.size() > 1) {
            Vec3d before = null;
            for (Vec3d point : BREADCRUMBS) {
                if (before != null) {
                    TracerLines.draw(
                        matrices.peek(), lines,
                        before.x - camera.x, before.y - camera.y, before.z - camera.z,
                        point.x - camera.x, point.y - camera.y, point.z - camera.z,
                        0xB8000000 | config.breadcrumbColor & 0x00FFFFFF, 1.25f
                    );
                }
                before = point;
            }
        }
        if (config.chunkBorders) drawChunkBorder(client, matrices, lines, camera, config.chunkBorderColor);
        if (config.logoutSpots) {
            for (LogoutSpot spot : LOGOUT_SPOTS.values()) {
                VertexRendering.drawOutline(
                    matrices, lines, PLAYER_BOX,
                    spot.pos.x - camera.x, spot.pos.y - camera.y, spot.pos.z - camera.z,
                    config.logoutSpotColor, 1.8f
                );
            }
        }
        List<WaypointStore.Waypoint> waypoints = config.waypoints ? WaypointStore.current(client) : List.of();
        for (WaypointStore.Waypoint waypoint : waypoints) {
            BlockPos pos = waypoint.pos();
            VertexRendering.drawOutline(
                matrices, lines, BLOCK_BOX,
                pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                config.waypointColor, 1.8f
            );
        }
        if (config.logoutSpots) {
            for (LogoutSpot spot : LOGOUT_SPOTS.values()) {
                String name = StreamerPrivacy.entityName(config.streamerMode, true, spot.name);
                drawLabel(context, "LOGOUT · " + name, spot.pos.add(0, 2.1, 0), camera, config.logoutSpotColor);
            }
        }
        for (WaypointStore.Waypoint waypoint : waypoints) {
            BlockPos pos = waypoint.pos();
            int distance = (int)Math.round(Math.sqrt(pos.getSquaredDistance(position(client.player))));
            drawLabel(context, waypoint.name() + " · " + distance + "m", Vec3d.ofCenter(pos).add(0, 0.85, 0), camera, config.waypointColor);
        }
    }

    private static void drawChunkBorder(MinecraftClient client, MatrixStack matrices, VertexConsumer lines, Vec3d camera, int color) {
        ChunkPos chunk = client.player.getChunkPos();
        double x0 = chunk.getStartX() - camera.x;
        double x1 = x0 + 16.0;
        double z0 = chunk.getStartZ() - camera.z;
        double z1 = z0 + 16.0;
        double y = Math.floor(client.player.getY()) - camera.y;
        line(matrices, lines, x0, y, z0, x1, y, z0, color);
        line(matrices, lines, x1, y, z0, x1, y, z1, color);
        line(matrices, lines, x1, y, z1, x0, y, z1, color);
        line(matrices, lines, x0, y, z1, x0, y, z0, color);
        line(matrices, lines, x0, y - 16, z0, x0, y + 16, z0, color);
        line(matrices, lines, x1, y - 16, z0, x1, y + 16, z0, color);
        line(matrices, lines, x1, y - 16, z1, x1, y + 16, z1, color);
        line(matrices, lines, x0, y - 16, z1, x0, y + 16, z1, color);
    }

    private static void line(MatrixStack matrices, VertexConsumer lines, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        TracerLines.draw(matrices.peek(), lines, x0, y0, z0, x1, y1, z1, color, 1.2f);
    }

    private static void drawLabel(WorldRenderContext context, String label, Vec3d pos, Vec3d camera, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = ArcaneFont.renderer(client);
        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply((Quaternionfc)context.worldState().cameraRenderState.orientation);
        matrices.scale(-0.025f, -0.025f, 0.025f);
        font.draw(label, -font.getWidth(label) / 2.0f, 0.0f, color, false, matrices.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000, 0xF000F0);
        matrices.pop();
    }

    private static WorldIntelPolicy.SearchKind classify(BlockState state) {
        if (state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_PORTAL_FRAME)) {
            return WorldIntelPolicy.SearchKind.PORTAL;
        }
        if (state.isOf(Blocks.SPAWNER) || state.isOf(Blocks.TRIAL_SPAWNER) || state.isOf(Blocks.VAULT)
            || state.isOf(Blocks.BEACON) || state.isOf(Blocks.LODESTONE) || state.isOf(Blocks.RESPAWN_ANCHOR)
            || state.isOf(Blocks.ANCIENT_DEBRIS)) {
            return WorldIntelPolicy.SearchKind.SEARCH;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return WorldIntelPolicy.classify(id == null ? "" : id.getPath());
    }

    private static Vec3d position(net.minecraft.entity.Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
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
        private final WorldChunk chunk;
        private final ChunkSection[] sections;
        private final long chunkKey;
        private final boolean includeSearch;
        private final boolean includePortal;
        private final ArrayList<SearchTarget> targets = new ArrayList<>();
        private int sectionIndex;
        private int blockIndex;

        private SearchJob(WorldChunk chunk, boolean includeSearch, boolean includePortal) {
            this.chunk = chunk;
            this.sections = chunk.getSectionArray();
            this.chunkKey = chunk.getPos().toLong();
            this.includeSearch = includeSearch;
            this.includePortal = includePortal;
        }

        private int step(int budget) {
            int visited = 0;
            while (sectionIndex < sections.length && visited < budget) {
                ChunkSection section = sections[sectionIndex];
                if (section.isEmpty() || blockIndex == 0 && !section.hasAny(state -> included(classify(state)))) {
                    sectionIndex++;
                    blockIndex = 0;
                    continue;
                }
                int x = blockIndex & 15;
                int z = blockIndex >>> 4 & 15;
                int y = blockIndex >>> 8 & 15;
                WorldIntelPolicy.SearchKind kind = classify(section.getBlockState(x, y, z));
                if (included(kind) && targets.size() < 512) {
                    int worldY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(sectionIndex)) + y;
                    targets.add(new SearchTarget(new BlockPos(chunk.getPos().getStartX() + x, worldY, chunk.getPos().getStartZ() + z), kind));
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

    private record PlayerSnapshot(String name, Vec3d pos) {
    }

    private record LogoutSpot(String name, Vec3d pos, long createdMillis) {
    }
}
