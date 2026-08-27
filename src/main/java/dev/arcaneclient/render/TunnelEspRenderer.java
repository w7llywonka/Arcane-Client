package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.model.TunnelSegment;
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
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

@Environment(value=EnvType.CLIENT)
public final class TunnelEspRenderer {
    private static final RenderPipeline TUNNEL_LINES = RenderPipelines.register((RenderPipeline)RenderPipeline.builder((RenderPipeline.Snippet[])new RenderPipeline.Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET}).withLocation(ArcaneClient.id("pipeline/tunnel_esp")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static final RenderLayer TUNNEL_LINE_TYPE = RenderLayer.of((String)"arcaneclient_tunnel_esp", (RenderSetup)RenderSetup.builder((RenderPipeline)TUNNEL_LINES).build());
    private static List<TunnelSegment> source = List.of();
    private static List<Target> targets = List.of();

    private TunnelEspRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TunnelEspRenderer::render);
    }

    public static void tick() {
        if (ArcaneSettingsScreen.isOpen(MinecraftClient.getInstance())) return;
        List<TunnelSegment> current = ArcaneClient.engine().tunnels();
        if (current == source) {
            return;
        }
        source = current;
        ArrayList<Target> rebuilt = new ArrayList<Target>(current.size());
        for (TunnelSegment segment : current) {
            VoxelShape shape = VoxelShapes.cuboid((double)0.03, (double)0.03, (double)0.03, (double)((double)(segment.maxX() - segment.minX()) - 0.03), (double)((double)(segment.maxY() - segment.minY()) - 0.03), (double)((double)(segment.maxZ() - segment.minZ()) - 0.03));
            rebuilt.add(new Target(segment.minX(), segment.minY(), segment.minZ(), shape, segment.type()));
        }
        targets = List.copyOf(rebuilt);
    }

    private static void render(WorldRenderContext context) {
        if (ArcaneSettingsScreen.isOpen(MinecraftClient.getInstance())) return;
        ArcaneConfig config = ArcaneClient.config();
        MatrixStack matrices = context.matrices();
        if (!config.tunnelEsp || targets.isEmpty() || matrices == null) {
            return;
        }
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = context.consumers().getBuffer(TUNNEL_LINE_TYPE);
        for (Target target : targets) {
            int color = target.type() == TunnelSegment.Type.TWO_BY_ONE ? config.tunnelTwoByOneColor : config.tunnelThreeByThreeColor;
            VertexRendering.drawOutline((MatrixStack)matrices, (VertexConsumer)lines, (VoxelShape)target.shape(), (double)((double)target.x() - camera.x), (double)((double)target.y() - camera.y), (double)((double)target.z() - camera.z), (int)color, (float)1.5f);
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(int x, int y, int z, VoxelShape shape, TunnelSegment.Type type) {
    }
}
