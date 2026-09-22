package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

@Environment(value=EnvType.CLIENT)
public final class ActivityClusterLabelRenderer {
    private static final String LABEL = "BASE CANDIDATE";
    private static List<TraceEngine.ActivityClusterCandidate> source = List.of();
    private static List<Target> targets = List.of();

    private ActivityClusterLabelRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ActivityClusterLabelRenderer::render);
    }

    public static void reset() {
        source = List.of();
        targets = List.of();
    }

    public static void tick(Minecraft client) {
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        if (!ArcaneClient.config().overlay || client.level == null) {
            reset();
            return;
        }
        List<TraceEngine.ActivityClusterCandidate> current = ArcaneClient.engine().activityClusters();
        if (current == source) {
            return;
        }
        source = current;
        ArrayList<Target> rebuilt = new ArrayList<Target>(current.size());
        for (TraceEngine.ActivityClusterCandidate candidate : current) {
            if (client.level.getChunkSource().getChunk(candidate.chunkX(), candidate.chunkZ(), false) == null) continue;
            int x = candidate.chunkX() * 16 + 8;
            int z = candidate.chunkZ() * 16 + 8;
            int y = Math.max(72, client.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 8);
            rebuilt.add(new Target(
                (double)x + 0.5, y, (double)z + 0.5,
                candidate.confidence(), candidate.members(), candidate.families()
            ));
        }
        targets = List.copyOf(rebuilt);
    }

    private static void render(LevelRenderContext context) {
        if (!ArcaneClient.config().overlay || targets.isEmpty() || context.poseStack() == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        Font font = ArcaneFont.renderer(client);
        for (Target target : targets) {
            String score = "CONF " + target.confidence() + "% · " + target.members() + " CH · " + target.families() + " FAM";
            Render263.text(context, font, LABEL, new Vec3(target.x(), target.y(), target.z()), 0.025f, -1, true);
            Render263.text(context, font, score, new Vec3(target.x(), target.y() - 0.25, target.z()), 0.025f, -3092272, true);
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(double x, double y, double z, int confidence, int members, int families) {
    }
}
