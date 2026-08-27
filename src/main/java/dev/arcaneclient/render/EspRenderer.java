package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.esp.BlockEntityEspClassifier;
import dev.arcaneclient.performance.PerformanceProfile;
import dev.arcaneclient.render.TracerLines;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector3fc;

@Environment(value=EnvType.CLIENT)
public final class EspRenderer {
    private static final VoxelShape BLOCK_BOX = VoxelShapes.cuboid((double)0.03, (double)0.03, (double)0.03, (double)0.97, (double)0.97, (double)0.97);
    private static final RenderPipeline ESP_LINES = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET}).withLocation(ArcaneClient.id("pipeline/storage_esp")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static final RenderLayer ESP_LINE_TYPE = RenderLayer.of((String)"arcaneclient_storage_esp", (RenderSetup)RenderSetup.builder((RenderPipeline)ESP_LINES).build());
    private static final RenderPipeline ESP_FILL = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET}).withLocation(ArcaneClient.id("pipeline/storage_esp_fill")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderLayer ESP_FILL_TYPE = RenderLayer.of((String)"arcaneclient_storage_esp_fill", (RenderSetup)RenderSetup.builder((RenderPipeline)ESP_FILL).translucent().build());
    private static List<Target> targets = List.of();
    private static long lastRefresh = Long.MIN_VALUE;

    private EspRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(EspRenderer::render);
    }

    public static void tick(MinecraftClient client) {
        if (ArcaneSettingsScreen.isOpen(client)) return;
        ArcaneConfig config = ArcaneClient.config();
        if ((!config.esp && !config.blockEntityDebug) || client.world == null || client.getCameraEntity() == null) {
            targets = List.of();
            return;
        }
        PerformanceProfile profile = config.performanceProfile();
        long gameTime = client.world.getTime();
        if (lastRefresh != Long.MIN_VALUE && gameTime >= lastRefresh && gameTime - lastRefresh < profile.snapshotRefreshTicks()) {
            return;
        }
        lastRefresh = gameTime;
        Entity cameraEntity = client.getCameraEntity();
        int centerX = ChunkSectionPos.getSectionCoord((int)cameraEntity.getBlockX());
        int centerZ = ChunkSectionPos.getSectionCoord((int)cameraEntity.getBlockZ());
        int radius = profile.storageRadiusChunks();
        int targetLimit = profile.storageTargetLimit();
        ArrayList<Target> refreshed = new ArrayList<Target>();
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                WorldChunk chunk = client.world.getChunkManager().getWorldChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
                    String path = id == null ? "unknown" : id.getPath();
                    int color = BlockEntityEspClassifier.color(path, config.esp, config.blockEntityDebug);
                    if (color == 0) continue;
                    refreshed.add(new Target(blockEntity.getPos().toImmutable(), color, BlockEntityEspClassifier.isStorageTarget(path)));
                    if (refreshed.size() < targetLimit) continue;
                    targets = List.copyOf(refreshed);
                    return;
                }
            }
        }
        targets = List.copyOf(refreshed);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (ArcaneSettingsScreen.isOpen(client) || (!config.esp && !config.blockEntityDebug) || client.world == null || targets.isEmpty()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        Entity cameraEntity = client.getCameraEntity();
        if (matrices == null || cameraEntity == null) {
            return;
        }
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = context.consumers().getBuffer(ESP_LINE_TYPE);
        PerformanceProfile profile = config.performanceProfile();
        VertexConsumer fills = profile.filledStorageBoxes() ? context.consumers().getBuffer(ESP_FILL_TYPE) : null;
        Vector3fc forward = client.gameRenderer.getCamera().getHorizontalPlane();
        for (Target target : targets) {
            BlockPos pos = target.pos();
            double x = (double)pos.getX() + 0.5 - camera.x;
            double y = (double)pos.getY() + 0.5 - camera.y;
            double z = (double)pos.getZ() + 0.5 - camera.z;
            VertexRendering.drawOutline((MatrixStack)matrices, (VertexConsumer)lines, (VoxelShape)BLOCK_BOX, (double)((double)pos.getX() - camera.x), (double)((double)pos.getY() - camera.y), (double)((double)pos.getZ() - camera.z), (int)target.color(), (float)2.0f);
            if (fills != null) EspRenderer.drawFilledBox(matrices.peek(), fills, (double)pos.getX() - camera.x, (double)pos.getY() - camera.y, (double)pos.getZ() - camera.z, 0x30000000 | target.color() & 0xFFFFFF);
            if (!target.storageTarget() || !config.storageTracers) continue;
            TracerLines.draw(matrices.peek(), lines, (double)forward.x() * 0.25, (double)forward.y() * 0.25, (double)forward.z() * 0.25, x, y, z, target.color(), 1.25f);
        }
    }

    private static void drawFilledBox(MatrixStack.Entry pose, VertexConsumer fills, double originX, double originY, double originZ, int color) {
        float x0 = (float)originX + 0.05f;
        float y0 = (float)originY + 0.05f;
        float z0 = (float)originZ + 0.05f;
        float x1 = (float)originX + 0.95f;
        float y1 = (float)originY + 0.95f;
        float z1 = (float)originZ + 0.95f;
        EspRenderer.quad(pose, fills, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        EspRenderer.quad(pose, fills, color, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        EspRenderer.quad(pose, fills, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        EspRenderer.quad(pose, fills, color, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        EspRenderer.quad(pose, fills, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        EspRenderer.quad(pose, fills, color, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }

    private static void quad(MatrixStack.Entry pose, VertexConsumer fills, int color, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        fills.vertex(pose, x0, y0, z0).color(color);
        fills.vertex(pose, x1, y1, z1).color(color);
        fills.vertex(pose, x2, y2, z2).color(color);
        fills.vertex(pose, x3, y3, z3).color(color);
    }

    public static int targetCount() {
        return targets.size();
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(BlockPos pos, int color, boolean storageTarget) {
    }
}
