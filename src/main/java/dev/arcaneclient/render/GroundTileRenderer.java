package dev.arcaneclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arcaneclient.TraceEngine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Cached terrain-draped tiles; never requests missing chunks or samples camera altitude. */
public final class GroundTileRenderer {
    private final Map<Long, Surface> surfaces = new HashMap<>();
    private ClientLevel cachedWorld;
    private int refreshCursor;
    private long rebuildTick = Long.MIN_VALUE;
    private int rebuildBudget;
    private List<TraceEngine.ChunkTile> convertedSource = List.of();
    private List<Tile> convertedTiles = List.of();

    public void clear() {
        surfaces.clear();
        cachedWorld = null;
        refreshCursor = 0;
        rebuildTick = Long.MIN_VALUE;
        rebuildBudget = 0;
        convertedSource = List.of();
        convertedTiles = List.of();
    }

    /** Shared terrain geometry with independent callers supplying their own colors. */
    public record Tile(int chunkX, int chunkZ, int fillColor, int outlineColor) { }

    int render(ClientLevel world, List<TraceEngine.ChunkTile> tiles, LevelRenderContext context, boolean throughWalls) {
        if (tiles != convertedSource) {
            convertedSource = tiles;
            convertedTiles = tiles.stream().map(tile -> new Tile(tile.chunkX(), tile.chunkZ(),
                ChunkTileRenderPolicy.fillColor(tile.score(), tile.flagged()),
                ChunkTileRenderPolicy.outlineColor(tile.score(), tile.flagged()))).toList();
        }
        return renderTiles(world, convertedTiles, context, throughWalls);
    }

    public int renderTiles(ClientLevel world, List<Tile> tiles, LevelRenderContext context, boolean throughWalls) {
        if (world != cachedWorld) { clear(); cachedWorld = world; }
        if (world == null || context.poseStack() == null) return 0;
        long now = System.nanoTime();
        long gameTick = world.getGameTime();
        if (gameTick != rebuildTick) {
            rebuildTick = gameTick;
            rebuildBudget = 8;
        }
        int count = 0;
        var visible = new HashSet<Long>();
        var rendered = new java.util.ArrayList<Tile>(tiles.size());
        int start = tiles.isEmpty() ? 0 : refreshCursor % tiles.size();
        for (int offset = 0; offset < tiles.size(); offset++) {
            int index = (start + offset) % tiles.size();
            var tile = tiles.get(index);
            long key = ChunkPos.pack(tile.chunkX(), tile.chunkZ());
            LevelChunk chunk = world.getChunkSource().getChunk(tile.chunkX(), tile.chunkZ(), ChunkStatus.FULL, false);
            if (chunk == null) { surfaces.remove(key); continue; }
            visible.add(key);
            Surface surface = surfaces.get(key);
            if (surface != null && surface.chunk != chunk) { surfaces.remove(key); surface = null; }
            if ((surface == null || now >= surface.expires) && rebuildBudget > 0) {
                int[] heights = new int[256];
                int startX = tile.chunkX() * 16, startZ = tile.chunkZ() * 16;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    heights[z * 16 + x] = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, startX + x, startZ + z);
                }
                surface = new Surface(chunk, heights, now + 500_000_000L);
                surfaces.put(key, surface);
                rebuildBudget--;
                // Rotate the budget so distant tiles also refresh at low frame rates.
                refreshCursor = (index + 1) % tiles.size();
            }
            if (surface == null) continue;
            rendered.add(tile);
            count++;
        }
        surfaces.keySet().retainAll(visible);
        if (!rendered.isEmpty()) {
            PoseStack poses = context.poseStack();
            Vec3 camera = Render263.camera(context);
            poses.pushPose();
            poses.translate(-camera.x, -camera.y, -camera.z);
            context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.debugFilledBox(), (pose, fills) -> {
                for (Tile tile : rendered) {
                    Surface surface = surfaces.get(ChunkPos.pack(tile.chunkX(), tile.chunkZ()));
                    if (surface != null) drawFill(tile, surface.heights, world.getMinY(), pose, fills);
                }
            });
            context.submitNodeCollector().submitCustomGeometry(poses, Render263.lines(throughWalls), (pose, lines) -> {
                for (Tile tile : rendered) {
                    if ((tile.outlineColor() >>> 24) == 0) continue;
                    Surface surface = surfaces.get(ChunkPos.pack(tile.chunkX(), tile.chunkZ()));
                    if (surface != null) drawOutline(tile, surface.heights, world.getMinY(), pose, lines);
                }
            });
            poses.popPose();
        }
        return count;
    }

    public double surfaceY(int chunkX, int chunkZ, int localX, int localZ) {
        Surface surface = surfaces.get(ChunkPos.pack(chunkX, chunkZ));
        return surface == null ? Double.NaN : ChunkTileRenderPolicy.surfaceY(surface.heights[localZ * 16 + localX]);
    }

    private static void drawFill(Tile tile, int[] heights, int bottom,
                             PoseStack.Pose pose, VertexConsumer fills) {
        double originX = tile.chunkX() * 16.0, originZ = tile.chunkZ() * 16.0;
        int fill = tile.fillColor();
        // Merge equal-height columns into strips; flat chunks need 16 quads rather than 256.
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16;) {
            int top = heights[z * 16 + x], end = x + 1;
            while (end < 16 && heights[z * 16 + end] == top) end++;
            if (top > bottom) {
                float y = (float)ChunkTileRenderPolicy.surfaceY(top);
                float x0 = (float)(originX + x), x1 = (float)(originX + end);
                float z0 = (float)(originZ + z), z1 = z0 + 1;
                fills.addVertex(pose, x0, y, z0).setColor(fill);
                fills.addVertex(pose, x0, y, z1).setColor(fill);
                fills.addVertex(pose, x1, y, z1).setColor(fill);
                fills.addVertex(pose, x1, y, z0).setColor(fill);
            }
            x = end;
        }
    }

    private static void drawOutline(Tile tile, int[] heights, int bottom,
                                    PoseStack.Pose pose, VertexConsumer lines) {
        double originX = tile.chunkX() * 16.0, originZ = tile.chunkZ() * 16.0;
        int outline = tile.outlineColor();
        float width = 2;
        for (int i = 0; i < 16; i++) {
            edge(lines, pose, heights[i], bottom, originX + i, originZ, originX + i + 1, originZ, outline, width);
            edge(lines, pose, heights[240 + i], bottom, originX + i, originZ + 16, originX + i + 1, originZ + 16, outline, width);
            edge(lines, pose, heights[i * 16], bottom, originX, originZ + i, originX, originZ + i + 1, outline, width);
            edge(lines, pose, heights[i * 16 + 15], bottom, originX + 16, originZ + i, originX + 16, originZ + i + 1, outline, width);
        }
    }

    private static void edge(VertexConsumer lines, PoseStack.Pose pose, int top, int bottom,
                             double x0, double z0, double x1, double z1, int color, float width) {
        if (top <= bottom) return;
        double y = ChunkTileRenderPolicy.surfaceY(top) + 0.015;
        TracerLines.draw(pose, lines, x0, y, z0, x1, y, z1, color, width);
    }

    private record Surface(LevelChunk chunk, int[] heights, long expires) { }
}
