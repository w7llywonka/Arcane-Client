package dev.arcaneclient.additions.susfinder;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.render.GroundTileRenderer;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.levelgen.Heightmap;

/** One terrain tile per candidate and one label per zone, independent of evidence-point overlays. */
public final class SusChunkFinderRenderer {
    private static final GroundTileRenderer GROUND = new GroundTileRenderer();
    private static int renderedTiles, renderedMarkers;

    private SusChunkFinderRenderer() { }

    static void register(SusChunkFinderController controller, Supplier<SusChunkFinderConfig> settings) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> render(context, controller, settings.get()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GROUND.clear());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ArcaneClient.id("sus_radar"),
            (draw, counter) -> renderRadar(draw, controller, settings.get()));
    }

    public static int renderedTileCount() { return renderedTiles; }
    public static int renderedMarkerCount() { return renderedMarkers; }
    public static double renderedSurfaceY(int chunkX, int chunkZ, int localX, int localZ) {
        return GROUND.surfaceY(chunkX, chunkZ, localX, localZ);
    }

    public static int withOpacity(int color, int opacity) {
        return color & 0xFFFFFF | Math.clamp(opacity, 0, 255) << 24;
    }

    private static boolean visible(Minecraft client, SusChunkFinderConfig config) {
        return config.enabled && client.level != null && client.player != null
            && !ArcaneVisibility.overlaysHidden() && !ArcaneSettingsScreen.isOpen(client);
    }

    private static void render(LevelRenderContext context, SusChunkFinderController controller, SusChunkFinderConfig config) {
        renderedTiles = renderedMarkers = 0;
        Minecraft client = Minecraft.getInstance();
        if (!visible(client, config) || context.poseStack() == null) { GROUND.clear(); return; }
        var camera = context.levelState().cameraRenderState.pos;
        if (config.highlights) {
            List<GroundTileRenderer.Tile> tiles = controller.candidates().stream().map(candidate -> {
                int color = candidate.inferred() ? config.inferredColor : config.color;
                return new GroundTileRenderer.Tile(candidate.chunkX(), candidate.chunkZ(),
                    withOpacity(color, config.fillOpacity), withOpacity(color, config.outlineOpacity));
            }).toList();
            renderedTiles = GROUND.renderTiles(client.level, tiles, context, true);
        } else GROUND.clear();
        if (!config.markers) return;
        Font font = ArcaneFont.renderer(client);
        // Highest scoring zones are first; cap the expensive text pass during dense flyovers.
        for (SusZoneGrouping.Zone zone : controller.zones().stream().limit(48).toList()) {
            int x = (int)Math.floor(zone.x()), z = (int)Math.floor(zone.z());
            if (client.level.getChunkSource().getChunk(x >> 4, z >> 4, false) == null) continue;
            double y = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2.0;
            var matrices = context.poseStack();
            matrices.pushPose();
            double dx = zone.x() - camera.x, dy = y - camera.y, dz = zone.z() - camera.z;
            matrices.translate(dx, dy, dz);
            matrices.rotate(context.levelState().cameraRenderState.orientation);
            float scale = SusMarkerLayout.scale(Math.sqrt(dx * dx + dy * dy + dz * dz));
            matrices.scale(scale, -scale, scale);
            var title = ArcaneFont.text(SusMarkerLayout.title(zone.score()));
            var detail = ArcaneFont.text(SusMarkerLayout.detail(zone.members().size(), zone.inferred()));
            int color = zone.inferred() ? config.inferredColor : config.color;
            context.submitNodeCollector().submitText(matrices, -font.width(title) / 2.0f, 0,
                title.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, color, 0x70000000, 0xF000F0, 0);
            context.submitNodeCollector().submitText(matrices, -font.width(detail) / 2.0f, 10,
                detail.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, 0xFFD7D7DD, 0x70000000, 0xF000F0, 0);
            matrices.popPose();
            renderedMarkers++;
        }
    }

    private static void renderRadar(GuiGraphicsExtractor draw, SusChunkFinderController controller, SusChunkFinderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (!visible(client, config) || !config.radar) return;
        int size = 94, x = client.getWindow().getGuiScaledWidth() - size - 8, y = 8;
        int centerX = x + size / 2, centerY = y + size / 2 + 6;
        double scale = 36.0 / Math.max(32, config.scanRange);
        draw.fill(x, y, x + size, y + size, 0xA0181620);
        var font = ArcaneFont.renderer(client);
        draw.text(font, ArcaneFont.text("SUS · N ↑"), x + 7, y + 5, config.color, false);
        draw.fill(centerX, y + 18, centerX + 1, y + size - 4, 0x306E6880);
        draw.fill(x + 4, centerY, x + size - 4, centerY + 1, 0x306E6880);
        draw.enableScissor(x + 3, y + 18, x + size - 3, y + size - 3);
        for (SusZoneGrouping.Zone zone : controller.zones()) {
            int px = centerX + (int)Math.round((zone.x() - client.player.getX()) * scale);
            int py = centerY + (int)Math.round((zone.z() - client.player.getZ()) * scale);
            draw.fill(px - 2, py - 2, px + 3, py + 3, zone.inferred() ? config.inferredColor : config.color);
        }
        draw.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFFFFF);
        draw.disableScissor();
    }
}
