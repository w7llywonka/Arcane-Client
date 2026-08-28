package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Quaternionfc;

@Environment(value=EnvType.CLIENT)
public final class StashLabelRenderer {
    private static final String LABEL = "POSSIBLE STASH FOUND";
    private static List<TraceEngine.StashCandidate> source = List.of();
    private static List<Target> targets = List.of();

    private StashLabelRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(StashLabelRenderer::render);
    }

    public static void tick(MinecraftClient client) {
        if (ArcaneSettingsScreen.isOpen(client)) return;
        if (!ArcaneClient.config().stashAlerts || client.world == null) {
            source = List.of();
            targets = List.of();
            return;
        }
        List<TraceEngine.StashCandidate> current = ArcaneClient.engine().stashCandidates();
        if (current == source) {
            return;
        }
        source = current;
        ArrayList<Target> rebuilt = new ArrayList<Target>(current.size());
        for (TraceEngine.StashCandidate candidate : current) {
            if (client.world.getChunkManager().getWorldChunk(candidate.chunkX(), candidate.chunkZ(), false) == null) continue;
            int x = candidate.chunkX() * 16 + 8;
            int z = candidate.chunkZ() * 16 + 8;
            int y = Math.max(72, client.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) + 8);
            rebuilt.add(new Target((double)x + 0.5, y, (double)z + 0.5, candidate.score()));
        }
        targets = List.copyOf(rebuilt);
    }

    private static void render(WorldRenderContext context) {
        if (!ArcaneClient.config().stashAlerts || targets.isEmpty() || context.matrices() == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (ArcaneSettingsScreen.isOpen(client)) return;
        Vec3d camera = context.worldState().cameraRenderState.pos;
        TextRenderer font = ArcaneFont.renderer(client);
        for (Target target : targets) {
            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(target.x() - camera.x, target.y() - camera.y, target.z() - camera.z);
            matrices.multiply((Quaternionfc)context.worldState().cameraRenderState.orientation);
            matrices.scale(-0.025f, -0.025f, 0.025f);
            String score = "CONFIDENCE " + target.score();
            StashLabelRenderer.drawCentered(font, LABEL, 0.0f, matrices, context, -1);
            StashLabelRenderer.drawCentered(font, score, 10.0f, matrices, context, -3092272);
            matrices.pop();
        }
    }

    private static void drawCentered(TextRenderer font, String text, float y, MatrixStack matrices, WorldRenderContext context, int color) {
        font.draw(text, (float)(-font.getWidth(text)) / 2.0f, y, color, false, matrices.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, -1342177280, 0xF000F0);
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(double x, double y, double z, int score) {
    }
}
