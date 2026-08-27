package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.render.TracerLines;
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
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Vector3fc;

@Environment(value=EnvType.CLIENT)
public final class ItemEspRenderer {
    private static final int REFRESH_TICKS = 5;
    private static final int MAX_TARGETS = 512;
    private static final double MAX_DISTANCE_SQUARED = 25600.0;
    private static final VoxelShape ITEM_BOX = VoxelShapes.cuboid((double)-0.28, (double)-0.05, (double)-0.28, (double)0.28, (double)0.55, (double)0.28);
    private static final RenderPipeline ITEM_LINES = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET}).withLocation(ArcaneClient.id("pipeline/item_esp")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static final RenderLayer ITEM_LINE_TYPE = RenderLayer.of((String)"arcaneclient_item_esp", (RenderSetup)RenderSetup.builder((RenderPipeline)ITEM_LINES).build());
    private static List<Target> targets = List.of();
    private static long lastRefresh = Long.MIN_VALUE;

    private ItemEspRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ItemEspRenderer::render);
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        if (!config.itemEsp || client.world == null || client.getCameraEntity() == null) {
            targets = List.of();
            return;
        }
        long gameTime = client.world.getTime();
        if (lastRefresh != Long.MIN_VALUE && gameTime >= lastRefresh && gameTime - lastRefresh < 5L) {
            return;
        }
        lastRefresh = gameTime;
        Entity camera = client.getCameraEntity();
        ArrayList<Target> refreshed = new ArrayList<Target>();
        for (Entity entity : client.world.getEntities()) {
            ItemEspCategory category;
            if (!(entity instanceof ItemEntity)) continue;
            ItemEntity item = (ItemEntity)entity;
            if (entity.squaredDistanceTo(camera) > 25600.0 || (category = ItemEspCategory.match(item.getStack())) == null || !config.itemEspEnabled(category)) continue;
            refreshed.add(new Target(item, config.itemEspColor(category)));
            if (refreshed.size() != 512) continue;
            break;
        }
        targets = List.copyOf(refreshed);
    }

    private static void render(WorldRenderContext context) {
        if (!ArcaneClient.config().itemEsp || targets.isEmpty()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = context.consumers().getBuffer(ITEM_LINE_TYPE);
        Vector3fc forward = MinecraftClient.getInstance().gameRenderer.getCamera().getHorizontalPlane();
        for (Target target : targets) {
            Vec3d position = target.entity().getEntityPos();
            double x = position.x - camera.x;
            double y = position.y + 0.25 - camera.y;
            double z = position.z - camera.z;
            VertexRendering.drawOutline((MatrixStack)matrices, (VertexConsumer)lines, (VoxelShape)ITEM_BOX, (double)(position.x - camera.x), (double)(position.y - camera.y), (double)(position.z - camera.z), (int)target.color(), (float)2.0f);
            if (!ArcaneClient.config().itemTracers) continue;
            TracerLines.draw(matrices.peek(), lines, (double)forward.x() * 0.25, (double)forward.y() * 0.25, (double)forward.z() * 0.25, x, y, z, target.color(), 1.25f);
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(ItemEntity entity, int color) {
    }
}
