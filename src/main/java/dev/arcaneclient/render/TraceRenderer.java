package dev.arcaneclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

@Environment(value=EnvType.CLIENT)
public final class TraceRenderer {
    private static volatile int renderedTileCount;
    private static volatile int renderedAmethystCount;
    private static final GroundTileRenderer GROUND_TILES = new GroundTileRenderer();

    private TraceRenderer() {
    }

    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GROUND_TILES.clear());
        // The debug-render phase is skipped when Minecraft's HUD is hidden (F1).
        // END_MAIN still runs and flushes custom buffers before the hand/GUI pass.
        LevelRenderEvents.COLLECT_SUBMITS.register(TraceRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        renderedTileCount = 0;
        renderedAmethystCount = 0;
        ArcaneConfig config = ArcaneClient.config();
        boolean regularLayers = EvidencePointRenderPolicy.anyLayerEnabled(config.enabled, config.overlay, config.evidencePoints);
        boolean observationLayers = config.amethystEsp || config.accessTrailEsp;
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(Minecraft.getInstance())
            || (!regularLayers && !observationLayers)) {
            return;
        }
        List<TraceEngine.ChunkMarker> markers = ArcaneClient.engine().markers();
        List<TraceEngine.ChunkTile> tileSnapshot = ArcaneClient.engine().tiles();
        PoseStack matrices = context.poseStack();
        if (matrices == null) {
            return;
        }
        Vec3 camera = Render263.camera(context);
        if (config.enabled && config.overlay && !tileSnapshot.isEmpty()) {
            renderedTileCount = GROUND_TILES.render(Minecraft.getInstance().level, tileSnapshot, context, true);
        } else {
            GROUND_TILES.clear();
        }
        if (config.enabled && config.evidencePoints && !markers.isEmpty()) {
            ArrayList<ChunkIntel> intelligence = new ArrayList<ChunkIntel>(markers.size());
            for (TraceEngine.ChunkMarker marker : markers) intelligence.add(marker.intel());
            List<EvidencePoint> selected = EvidencePointRenderPolicy.select(intelligence);
            context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.debugFilledBox(),
                (submittedPose, vertices) -> renderEvidencePoints(vertices, submittedPose, camera, selected));
        }
        if (observationLayers) {
            List<WorldObservation> observations = ArcaneClient.engine().worldObservations();
            context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.debugFilledBox(),
                (submittedPose, vertices) -> renderWorldObservations(vertices, submittedPose, camera, observations, config));
        }
    }

    public static int renderedTileCount() {
        return renderedTileCount;
    }

    public static int renderedAmethystCount() { return renderedAmethystCount; }

    public static double renderedTileSurfaceY(int chunkX, int chunkZ, int localX, int localZ) {
        return GROUND_TILES.surfaceY(chunkX, chunkZ, localX, localZ);
    }

    private static void renderEvidencePoints(
        VertexConsumer vertices,
        PoseStack.Pose pose,
        Vec3 camera,
        List<EvidencePoint> evidence
    ) {
        for (EvidencePoint point : evidence) {
            float x = (float)((double)point.position().x() + 0.5 - camera.x);
            float y = (float)((double)point.position().y() + 0.5 - camera.y);
            float z = (float)((double)point.position().z() + 0.5 - camera.z);
            float radius = point.live() ? 0.28F : 0.20F;
            if (point.observations() >= 3) radius += 0.04F;
            int color = EvidencePointRenderPolicy.color(point.family());

            vertices.addVertex(pose, x - radius, y - radius, z).setColor(color);
            vertices.addVertex(pose, x - radius, y + radius, z).setColor(color);
            vertices.addVertex(pose, x + radius, y + radius, z).setColor(color);
            vertices.addVertex(pose, x + radius, y - radius, z).setColor(color);

            vertices.addVertex(pose, x - radius, y, z - radius).setColor(color);
            vertices.addVertex(pose, x - radius, y, z + radius).setColor(color);
            vertices.addVertex(pose, x + radius, y, z + radius).setColor(color);
            vertices.addVertex(pose, x + radius, y, z - radius).setColor(color);

            vertices.addVertex(pose, x, y - radius, z - radius).setColor(color);
            vertices.addVertex(pose, x, y + radius, z - radius).setColor(color);
            vertices.addVertex(pose, x, y + radius, z + radius).setColor(color);
            vertices.addVertex(pose, x, y - radius, z + radius).setColor(color);
        }
    }

    private static void renderWorldObservations(
        VertexConsumer vertices,
        PoseStack.Pose pose,
        Vec3 camera,
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
                renderedAmethystCount++;
            } else {
                float radius = 0.31F;
                renderCross(vertices, pose, x, y, z, radius, withAlpha(config.accessTrailEspColor, 0xA8));
            }
        }
    }

    private static void renderDiamond(
        VertexConsumer vertices,
        PoseStack.Pose pose,
        float x,
        float y,
        float z,
        float radius,
        float height,
        int color
    ) {
        vertices.addVertex(pose, x, y - height, z).setColor(color);
        vertices.addVertex(pose, x - radius, y, z).setColor(color);
        vertices.addVertex(pose, x, y + height, z).setColor(color);
        vertices.addVertex(pose, x + radius, y, z).setColor(color);

        vertices.addVertex(pose, x, y - height, z).setColor(color);
        vertices.addVertex(pose, x, y, z - radius).setColor(color);
        vertices.addVertex(pose, x, y + height, z).setColor(color);
        vertices.addVertex(pose, x, y, z + radius).setColor(color);

        vertices.addVertex(pose, x, y, z - radius).setColor(color);
        vertices.addVertex(pose, x - radius, y, z).setColor(color);
        vertices.addVertex(pose, x, y, z + radius).setColor(color);
        vertices.addVertex(pose, x + radius, y, z).setColor(color);
    }

    private static void renderCross(
        VertexConsumer vertices,
        PoseStack.Pose pose,
        float x,
        float y,
        float z,
        float radius,
        int color
    ) {
        vertices.addVertex(pose, x - radius, y - radius, z).setColor(color);
        vertices.addVertex(pose, x - radius, y + radius, z).setColor(color);
        vertices.addVertex(pose, x + radius, y + radius, z).setColor(color);
        vertices.addVertex(pose, x + radius, y - radius, z).setColor(color);
        vertices.addVertex(pose, x, y - radius, z - radius).setColor(color);
        vertices.addVertex(pose, x, y + radius, z - radius).setColor(color);
        vertices.addVertex(pose, x, y + radius, z + radius).setColor(color);
        vertices.addVertex(pose, x, y - radius, z + radius).setColor(color);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | (alpha & 0xFF) << 24;
    }

}
