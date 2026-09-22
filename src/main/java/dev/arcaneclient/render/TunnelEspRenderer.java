package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.model.TunnelSegment;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(value=EnvType.CLIENT)
public final class TunnelEspRenderer {
    private static List<TunnelSegment> source = List.of();
    private static List<Target> targets = List.of();

    private TunnelEspRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(TunnelEspRenderer::render);
    }

    public static void tick() {
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(Minecraft.getInstance())) return;
        List<TunnelSegment> current = ArcaneClient.engine().tunnels();
        if (current == source) {
            return;
        }
        source = current;
        ArrayList<Target> rebuilt = new ArrayList<Target>(current.size());
        for (TunnelSegment segment : current) {
            VoxelShape shape = Shapes.box((double)0.03, (double)0.03, (double)0.03, (double)((double)(segment.maxX() - segment.minX()) - 0.03), (double)((double)(segment.maxY() - segment.minY()) - 0.03), (double)((double)(segment.maxZ() - segment.minZ()) - 0.03));
            rebuilt.add(new Target(segment.minX(), segment.minY(), segment.minZ(), shape, segment.type()));
        }
        targets = List.copyOf(rebuilt);
    }

    private static void render(LevelRenderContext context) {
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(Minecraft.getInstance())) return;
        ArcaneConfig config = ArcaneClient.config();
        if (!config.tunnelEsp || targets.isEmpty() || context.poseStack() == null) {
            return;
        }
        Render263.Batch batch = Render263.batch(context);
        for (Target target : targets) {
            int color = target.type() == TunnelSegment.Type.TWO_BY_ONE ? config.tunnelTwoByOneColor : config.tunnelThreeByThreeColor;
            batch.outline(target.shape(), target.x(), target.y(), target.z(), color, 1.5f, true);
        }
        batch.submit();
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(int x, int y, int z, VoxelShape shape, TunnelSegment.Type type) {
    }
}
