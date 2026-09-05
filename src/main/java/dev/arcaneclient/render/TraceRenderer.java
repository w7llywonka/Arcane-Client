package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.model.ChunkIntel;
import dev.arcaneclient.model.EvidencePoint;
import dev.arcaneclient.model.WorldObservation;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
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
    private static volatile int renderedTileCount;
    private static final GroundTileRenderer GROUND_TILES = new GroundTileRenderer();
    private static final RenderPipeline THROUGH_WALL_TILE_LINES = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET}).withLocation(Identifier.of((String)"arcaneclient", (String)"pipeline/chunk_tile_outlines_through_walls")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static final RenderLayer THROUGH_WALL_TILE_LINE_TYPE = RenderLayer.of((String)"arcaneclient_chunk_tile_outlines_through_walls", (RenderSetup)RenderSetup.builder((RenderPipeline)THROUGH_WALL_TILE_LINES).build());
    private static final RenderPipeline THROUGH_WALL_TILES = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET}).withLocation(Identifier.of((String)"arcaneclient", (String)"pipeline/chunk_tiles_through_walls")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderLayer THROUGH_WALL_TILE_TYPE = RenderLayer.of((String)"arcaneclient_chunk_tiles_through_walls", (RenderSetup)RenderSetup.builder((RenderPipeline)THROUGH_WALL_TILES).translucent().build());
    private static final RenderPipeline THROUGH_WALL_POINTS = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET}).withLocation(Identifier.of((String)"arcaneclient", (String)"pipeline/evidence_points_through_walls")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderLayer THROUGH_WALL_POINT_TYPE = RenderLayer.of((String)"arcaneclient_evidence_points_through_walls", (RenderSetup)RenderSetup.builder((RenderPipeline)THROUGH_WALL_POINTS).translucent().build());

    private TraceRenderer() {
    }

    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GROUND_TILES.clear());
        // The debug-render phase is skipped when Minecraft's HUD is hidden (F1).
        // END_MAIN still runs and flushes custom buffers before the hand/GUI pass.
        WorldRenderEvents.END_MAIN.register(TraceRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        renderedTileCount = 0;
        ArcaneConfig config = ArcaneClient.config();
        boolean regularLayers = EvidencePointRenderPolicy.anyLayerEnabled(config.enabled, config.overlay, config.evidencePoints);
        boolean observationLayers = config.amethystEsp || config.accessTrailEsp;
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(MinecraftClient.getInstance())
            || (!regularLayers && !observationLayers)) {
            return;
        }
        List<TraceEngine.ChunkMarker> markers = ArcaneClient.engine().markers();
        List<TraceEngine.ChunkTile> tileSnapshot = ArcaneClient.engine().tiles();
        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }
        Vec3d camera = context.worldState().cameraRenderState.pos;
        MatrixStack.Entry pose = matrices.peek();
        if (config.enabled && config.overlay && !tileSnapshot.isEmpty()) {
            renderedTileCount = GROUND_TILES.render(MinecraftClient.getInstance().world, tileSnapshot, pose, camera,
                context.consumers(), THROUGH_WALL_TILE_TYPE, THROUGH_WALL_TILE_LINE_TYPE);
        } else {
            GROUND_TILES.clear();
        }
        if (config.enabled && config.evidencePoints && !markers.isEmpty()) {
            ArrayList<ChunkIntel> intelligence = new ArrayList<ChunkIntel>(markers.size());
            for (TraceEngine.ChunkMarker marker : markers) intelligence.add(marker.intel());
            renderEvidencePoints(context.consumers().getBuffer(THROUGH_WALL_POINT_TYPE), pose, camera, EvidencePointRenderPolicy.select(intelligence));
        }
        if (observationLayers) {
            renderWorldObservations(
                context.consumers().getBuffer(THROUGH_WALL_POINT_TYPE), pose, camera,
                ArcaneClient.engine().worldObservations(), config
            );
        }
    }

    public static int renderedTileCount() {
        return renderedTileCount;
    }

    public static double renderedTileSurfaceY(int chunkX, int chunkZ, int localX, int localZ) {
        return GROUND_TILES.surfaceY(chunkX, chunkZ, localX, localZ);
    }

    private static void renderEvidencePoints(
        VertexConsumer vertices,
        MatrixStack.Entry pose,
        Vec3d camera,
        List<EvidencePoint> evidence
    ) {
        for (EvidencePoint point : evidence) {
            float x = (float)((double)point.position().x() + 0.5 - camera.x);
            float y = (float)((double)point.position().y() + 0.5 - camera.y);
            float z = (float)((double)point.position().z() + 0.5 - camera.z);
            float radius = point.live() ? 0.28F : 0.20F;
            if (point.observations() >= 3) radius += 0.04F;
            int color = EvidencePointRenderPolicy.color(point.family());

            vertices.vertex(pose, x - radius, y - radius, z).color(color);
            vertices.vertex(pose, x - radius, y + radius, z).color(color);
            vertices.vertex(pose, x + radius, y + radius, z).color(color);
            vertices.vertex(pose, x + radius, y - radius, z).color(color);

            vertices.vertex(pose, x - radius, y, z - radius).color(color);
            vertices.vertex(pose, x - radius, y, z + radius).color(color);
            vertices.vertex(pose, x + radius, y, z + radius).color(color);
            vertices.vertex(pose, x + radius, y, z - radius).color(color);

            vertices.vertex(pose, x, y - radius, z - radius).color(color);
            vertices.vertex(pose, x, y + radius, z - radius).color(color);
            vertices.vertex(pose, x, y + radius, z + radius).color(color);
            vertices.vertex(pose, x, y - radius, z + radius).color(color);
        }
    }

    private static void renderWorldObservations(
        VertexConsumer vertices,
        MatrixStack.Entry pose,
        Vec3d camera,
        List<WorldObservation> observations,
        ArcaneConfig config
    ) {
        for (WorldObservation observation : observations) {
            boolean amethyst = observation.kind() == WorldObservation.Kind.AMETHYST_SHARD;
            if ((amethyst && !config.amethystEsp) || (!amethyst && !config.accessTrailEsp)) continue;
            int range = amethyst ? config.amethystEspRange : config.accessTrailEspRange;
            double dx = observation.position().x() + 0.5 - camera.x;
            double dy = observation.position().y() + 0.5 - camera.y;
            double dz = observation.position().z() + 0.5 - camera.z;
            if (dx * dx + dy * dy + dz * dz > (double)range * range) continue;
            float x = (float)dx;
            float y = (float)dy;
            float z = (float)dz;
            if (amethyst) {
                float radius = switch (observation.stage()) {
                    case 0 -> 0.11F;
                    case 1 -> 0.16F;
                    case 2 -> 0.22F;
                    case 3 -> 0.30F;
                    default -> 0.18F;
                };
                float height = switch (observation.stage()) {
                    case 0 -> 0.20F;
                    case 1 -> 0.29F;
                    case 2 -> 0.39F;
                    case 3 -> 0.52F;
                    default -> 0.32F;
                };
                int alpha = observation.live() ? 0xE8 : 0xB8;
                renderDiamond(vertices, pose, x, y, z, radius, height, withAlpha(config.amethystEspColor, alpha));
            } else {
                float radius = 0.31F;
                renderCross(vertices, pose, x, y, z, radius, withAlpha(config.accessTrailEspColor, 0xA8));
            }
        }
    }

    private static void renderDiamond(
        VertexConsumer vertices,
        MatrixStack.Entry pose,
        float x,
        float y,
        float z,
        float radius,
        float height,
        int color
    ) {
        vertices.vertex(pose, x, y - height, z).color(color);
        vertices.vertex(pose, x - radius, y, z).color(color);
        vertices.vertex(pose, x, y + height, z).color(color);
        vertices.vertex(pose, x + radius, y, z).color(color);

        vertices.vertex(pose, x, y - height, z).color(color);
        vertices.vertex(pose, x, y, z - radius).color(color);
        vertices.vertex(pose, x, y + height, z).color(color);
        vertices.vertex(pose, x, y, z + radius).color(color);

        vertices.vertex(pose, x, y, z - radius).color(color);
        vertices.vertex(pose, x - radius, y, z).color(color);
        vertices.vertex(pose, x, y, z + radius).color(color);
        vertices.vertex(pose, x + radius, y, z).color(color);
    }

    private static void renderCross(
        VertexConsumer vertices,
        MatrixStack.Entry pose,
        float x,
        float y,
        float z,
        float radius,
        int color
    ) {
        vertices.vertex(pose, x - radius, y - radius, z).color(color);
        vertices.vertex(pose, x - radius, y + radius, z).color(color);
        vertices.vertex(pose, x + radius, y + radius, z).color(color);
        vertices.vertex(pose, x + radius, y - radius, z).color(color);
        vertices.vertex(pose, x, y - radius, z - radius).color(color);
        vertices.vertex(pose, x, y + radius, z - radius).color(color);
        vertices.vertex(pose, x, y + radius, z + radius).color(color);
        vertices.vertex(pose, x, y - radius, z + radius).color(color);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | (alpha & 0xFF) << 24;
    }

}
