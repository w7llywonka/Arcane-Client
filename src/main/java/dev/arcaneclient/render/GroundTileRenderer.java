package dev.arcaneclient.render;

import dev.arcaneclient.TraceEngine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Cached terrain-draped tiles; never requests missing chunks or samples camera altitude. */
final class GroundTileRenderer {
    private final Map<Long, Surface> surfaces = new HashMap<>();
    private ClientWorld cachedWorld;
    private int refreshCursor;

    void clear() { surfaces.clear(); cachedWorld = null; refreshCursor = 0; }

    int render(ClientWorld world, List<TraceEngine.ChunkTile> tiles, MatrixStack.Entry pose,
               Vec3d camera, VertexConsumerProvider consumers, RenderLayer fillLayer, RenderLayer lineLayer) {
        if (world != cachedWorld) { clear(); cachedWorld = world; }
        if (world == null) return 0;
        long now = System.nanoTime();
        int rebuildBudget = 4, count = 0;
        var visible = new HashSet<Long>();
        VertexConsumer fills = consumers.getBuffer(fillLayer);
        int start = tiles.isEmpty() ? 0 : refreshCursor % tiles.size();
        for (int offset = 0; offset < tiles.size(); offset++) {
            int index = (start + offset) % tiles.size();
            var tile = tiles.get(index);
            long key = ChunkPos.toLong(tile.chunkX(), tile.chunkZ());
            WorldChunk chunk = world.getChunkManager().getChunk(tile.chunkX(), tile.chunkZ(), ChunkStatus.FULL, false);
            if (chunk == null) { surfaces.remove(key); continue; }
            visible.add(key);
            Surface surface = surfaces.get(key);
            if (surface != null && surface.chunk != chunk) { surfaces.remove(key); surface = null; }
            if ((surface == null || now >= surface.expires) && rebuildBudget > 0) {
                int[] heights = new int[256];
                int startX = tile.chunkX() * 16, startZ = tile.chunkZ() * 16;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    heights[z * 16 + x] = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, startX + x, startZ + z);
                }
                surface = new Surface(chunk, heights, now + 500_000_000L);
                surfaces.put(key, surface);
                rebuildBudget--;
                // Rotate the budget so distant tiles also refresh at low frame rates.
                refreshCursor = (index + 1) % tiles.size();
            }
            if (surface == null) continue;
            drawFill(tile, surface.heights, world.getBottomY(), pose, camera, fills);
            count++;
        }
        surfaces.keySet().retainAll(visible);
        // Finish all fills before obtaining another layer: providers may flush on switching.
        VertexConsumer lines = consumers.getBuffer(lineLayer);
        for (var tile : tiles) {
            if (!tile.flagged()) continue;
            Surface surface = surfaces.get(ChunkPos.toLong(tile.chunkX(), tile.chunkZ()));
            if (surface != null) drawOutline(tile, surface.heights, world.getBottomY(), pose, camera, lines);
        }
        return count;
    }

    double surfaceY(int chunkX, int chunkZ, int localX, int localZ) {
        Surface surface = surfaces.get(ChunkPos.toLong(chunkX, chunkZ));
        return surface == null ? Double.NaN : ChunkTileRenderPolicy.surfaceY(surface.heights[localZ * 16 + localX]);
    }

    private static void drawFill(TraceEngine.ChunkTile tile, int[] heights, int bottom,
                             MatrixStack.Entry pose, Vec3d camera, VertexConsumer fills) {
        double originX = tile.chunkX() * 16.0 - camera.x, originZ = tile.chunkZ() * 16.0 - camera.z;
        int fill = ChunkTileRenderPolicy.fillColor(tile.score(), tile.flagged());
        // Merge equal-height columns into strips; flat chunks need 16 quads rather than 256.
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16;) {
            int top = heights[z * 16 + x], end = x + 1;
            while (end < 16 && heights[z * 16 + end] == top) end++;
            if (top > bottom) {
                float y = (float)(ChunkTileRenderPolicy.surfaceY(top) - camera.y);
                float x0 = (float)(originX + x), x1 = (float)(originX + end);
                float z0 = (float)(originZ + z), z1 = z0 + 1;
                fills.vertex(pose, x0, y, z0).color(fill);
                fills.vertex(pose, x0, y, z1).color(fill);
                fills.vertex(pose, x1, y, z1).color(fill);
                fills.vertex(pose, x1, y, z0).color(fill);
            }
            x = end;
        }
    }

    private static void drawOutline(TraceEngine.ChunkTile tile, int[] heights, int bottom,
                                    MatrixStack.Entry pose, Vec3d camera, VertexConsumer lines) {
        double originX = tile.chunkX() * 16.0 - camera.x, originZ = tile.chunkZ() * 16.0 - camera.z;
        int outline = ChunkTileRenderPolicy.outlineColor(tile.score(), tile.flagged());
        float width = tile.flagged() ? 2 : 1;
        for (int i = 0; i < 16; i++) {
            edge(lines, pose, camera.y, heights[i], bottom, originX + i, originZ, originX + i + 1, originZ, outline, width);
            edge(lines, pose, camera.y, heights[240 + i], bottom, originX + i, originZ + 16, originX + i + 1, originZ + 16, outline, width);
            edge(lines, pose, camera.y, heights[i * 16], bottom, originX, originZ + i, originX, originZ + i + 1, outline, width);
            edge(lines, pose, camera.y, heights[i * 16 + 15], bottom, originX + 16, originZ + i, originX + 16, originZ + i + 1, outline, width);
        }
    }

    private static void edge(VertexConsumer lines, MatrixStack.Entry pose, double cameraY, int top, int bottom,
                             double x0, double z0, double x1, double z1, int color, float width) {
        if (top <= bottom) return;
        double y = ChunkTileRenderPolicy.surfaceY(top) + 0.015 - cameraY;
        TracerLines.draw(pose, lines, x0, y, z0, x1, y, z1, color, width);
    }

    private record Surface(WorldChunk chunk, int[] heights, long expires) { }
}
