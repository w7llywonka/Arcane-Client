package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

@Environment(value=EnvType.CLIENT)
public final class TraceRenderer {
    private static final double MARKER_Y = 64.0;
    private static final RenderPipeline THROUGH_WALL_TILES = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET}).withLocation(Identifier.of((String)"arcaneclient", (String)"pipeline/chunk_tiles_through_walls")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderLayer THROUGH_WALL_TILE_TYPE = RenderLayer.of((String)"arcaneclient_chunk_tiles_through_walls", (RenderSetup)RenderSetup.builder((RenderPipeline)THROUGH_WALL_TILES).translucent().build());

    private TraceRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TraceRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        ArcaneConfig config = ArcaneClient.config();
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(MinecraftClient.getInstance()) || !config.enabled || !config.overlay) {
            return;
        }
        List<TraceEngine.ChunkMarker> markers = ArcaneClient.engine().markers();
        MatrixStack matrices = context.matrices();
        if (markers.isEmpty() || matrices == null) {
            return;
        }
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer tiles = context.consumers().getBuffer(THROUGH_WALL_TILE_TYPE);
        MatrixStack.Entry pose = matrices.peek();
        for (TraceEngine.ChunkMarker marker : markers) {
            float x0 = (float)((double)marker.chunkX() * 16.0 - camera.x);
            float x1 = x0 + 16.0f;
            float y = (float)(64.0 - camera.y);
            float z0 = (float)((double)marker.chunkZ() * 16.0 - camera.z);
            float z1 = z0 + 16.0f;
            int color = TraceRenderer.colorFor(marker.score());
            tiles.vertex(pose, x0, y, z0).color(color);
            tiles.vertex(pose, x0, y, z1).color(color);
            tiles.vertex(pose, x1, y, z1).color(color);
            tiles.vertex(pose, x1, y, z0).color(color);
        }
    }

    private static int colorFor(int score) {
        if (score >= 75) {
            return 1895777605;
        }
        if (score >= 50) {
            return 1627364654;
        }
        return 1358944330;
    }
}
