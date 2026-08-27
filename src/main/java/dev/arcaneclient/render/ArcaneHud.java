package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

@Environment(EnvType.CLIENT)
public final class ArcaneHud {
    private static final Identifier HUD_ID = ArcaneClient.id("nearby_chunks");
    private static final int RADIUS = 3;
    private static final int GRID_SIZE = 7;
    private static final int CELL_SIZE = 14;
    private static final int PANEL_X = 5;
    private static final int PANEL_Y = 5;
    private static final int HEADER_HEIGHT = 16;
    private static final int PADDING = 3;

    private static Map<Long, TraceEngine.ChunkMarker> cachedMarkers = Map.of();
    private static long cachedTickBucket = Long.MIN_VALUE;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;

    private ArcaneHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, (graphics, tickCounter) -> render(graphics));
    }

    private static void render(DrawContext graphics) {
        ArcaneConfig config = ArcaneClient.config();
        MinecraftClient client = MinecraftClient.getInstance();
        if (ArcaneSettingsScreen.isOpen(client)) return;
        if (client.player == null) {
            return;
        }
        if (config.hud) {
            renderRadar(graphics, client, config);
        }
        if (config.chunkAnalysis) {
            renderChunkAnalysis(graphics, client, config);
        }
    }

    private static void renderRadar(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        TraceEngine engine = ArcaneClient.engine();
        ChunkPos center = client.player.getChunkPos();
        Map<Long, TraceEngine.ChunkMarker> markers = nearbyMarkers(engine, center);
        int accent = accent(config.uiTheme);
        int secondary = secondary(config.uiTheme);
        int gridPixels = GRID_SIZE * CELL_SIZE;
        int panelWidth = gridPixels + PADDING * 2;
        int panelHeight = HEADER_HEIGHT + gridPixels + PADDING;

        graphics.fill(PANEL_X + 3, PANEL_Y + 4, PANEL_X + panelWidth + 3, PANEL_Y + panelHeight + 4, 0x55000000);
        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + panelWidth, PANEL_Y + panelHeight, 0xEC120F20);
        graphics.drawStrokedRectangle(PANEL_X, PANEL_Y, panelWidth, panelHeight, 0xFF4B4168);
        graphics.fill(PANEL_X + 1, PANEL_Y + 1, PANEL_X + panelWidth - 1, PANEL_Y + 3, accent);

        String state = config.enabled ? "ON" : "PAUSED";
        String header = "ARCANE  " + state + (FreecamController.isActive() ? "  FC" : "") + (config.esp ? "  ESP" : "");
        graphics.drawText(font, header, PANEL_X + PADDING, PANEL_Y + 6, config.enabled ? 0xFFF8FAFC : 0xFFFB7185, true);
        String flagged = Integer.toString(engine.flaggedCount());
        graphics.drawText(font, flagged, PANEL_X + panelWidth - PADDING - font.getWidth(flagged), PANEL_Y + 6, secondary, false);

        int gridX = PANEL_X + PADDING;
        int gridY = PANEL_Y + HEADER_HEIGHT;
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                int x = gridX + (dx + RADIUS) * CELL_SIZE;
                int y = gridY + (dz + RADIUS) * CELL_SIZE;
                TraceEngine.ChunkMarker marker = markers.get(key(chunkX, chunkZ));
                int score = marker == null ? 0 : marker.score();
                graphics.fill(x + 1, y + 1, x + CELL_SIZE - 1, y + CELL_SIZE - 1, score == 0 ? 0x50231D36 : qualityColor(score, config.threshold));
                if (score > 0) {
                    int scoreColor = score >= config.threshold && score < 75 ? 0xFF15111F : 0xFFF8FAFC;
                    graphics.drawCenteredTextWithShadow(font, Integer.toString(Math.min(99, score)), x + CELL_SIZE / 2, y + 3, scoreColor);
                }
                if (dx == 0 && dz == 0) {
                    graphics.drawStrokedRectangle(x, y, CELL_SIZE, CELL_SIZE, accent);
                    if (score == 0) {
                        graphics.drawCenteredTextWithShadow(font, "+", x + CELL_SIZE / 2, y + 3, secondary);
                    }
                }
            }
        }
    }

    private static void renderChunkAnalysis(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        TraceEngine engine = ArcaneClient.engine();
        ChunkPos chunk = client.player.getChunkPos();
        TraceEngine.ChunkMarker marker = engine.snapshotMarkerAt(chunk.x, chunk.z);
        int accent = accent(config.uiTheme);
        int secondary = secondary(config.uiTheme);
        int panelWidth = 212;
        int x = graphics.getScaledWindowWidth() - panelWidth - 6;
        int y = 6;
        int reasonCount = marker == null ? 0 : Math.min(4, marker.reasons().size());
        int height = marker == null || marker.score() == 0 ? 47 : 40 + reasonCount * 11;

        graphics.fill(x + 3, y + 4, x + panelWidth + 3, y + height + 4, 0x55000000);
        graphics.fill(x, y, x + panelWidth, y + height, 0xEC120F20);
        graphics.drawStrokedRectangle(x, y, panelWidth, height, 0xFF4B4168);
        graphics.fill(x + 1, y + 1, x + 4, y + height - 1, accent);
        graphics.drawText(font, "CURRENT CHUNK", x + 10, y + 7, 0xFFF8FAFC, true);
        graphics.drawText(font, chunk.x + ", " + chunk.z, x + 10, y + 20, secondary, false);

        if (marker == null || marker.score() == 0) {
            String empty = font.trimToWidth("No activity evidence detected", panelWidth - 20);
            graphics.drawText(font, empty, x + 10, y + 33, 0xFF94A3B8, false);
            return;
        }

        boolean growthSite = isGrowthSite(engine, chunk);
        String score = growthSite ? "GROWTH SITE  " + marker.score() : "SCORE  " + marker.score();
        int scoreX = x + panelWidth - 9 - font.getWidth(score);
        graphics.drawText(font, score, scoreX, y + 7, growthSite ? 0xFFFB7185 : accent, true);

        int lineY = y + 34;
        for (int index = 0; index < reasonCount; index++) {
            String reason = font.trimToWidth(marker.reasons().get(index), panelWidth - 25);
            graphics.drawText(font, "> " + reason, x + 10, lineY, 0xFFD8D3E4, false);
            lineY += 11;
        }
    }

    private static boolean isGrowthSite(TraceEngine engine, ChunkPos chunk) {
        for (TraceEngine.GrowthCandidate candidate : engine.growthCandidates()) {
            if (candidate.chunkX() == chunk.x && candidate.chunkZ() == chunk.z) return true;
        }
        return false;
    }
    private static Map<Long, TraceEngine.ChunkMarker> nearbyMarkers(TraceEngine engine, ChunkPos center) {
        long tickBucket = engine.currentTick() / 10L;
        if (tickBucket == cachedTickBucket && center.x == cachedCenterX && center.z == cachedCenterZ) {
            return cachedMarkers;
        }

        HashMap<Long, TraceEngine.ChunkMarker> refreshed = new HashMap<>();
        for (TraceEngine.ChunkMarker marker : engine.nearby(center.x, center.z, RADIUS)) {
            refreshed.put(key(marker.chunkX(), marker.chunkZ()), marker);
        }
        cachedMarkers = Map.copyOf(refreshed);
        cachedTickBucket = tickBucket;
        cachedCenterX = center.x;
        cachedCenterZ = center.z;
        return cachedMarkers;
    }

    private static int accent(int theme) {
        return switch (Math.floorMod(theme, 3)) {
            case 1 -> 0xFFFB7185;
            case 2 -> 0xFF34D399;
            default -> 0xFFA855F7;
        };
    }

    private static int secondary(int theme) {
        return switch (Math.floorMod(theme, 3)) {
            case 1 -> 0xFFF59E0B;
            case 2 -> 0xFF2DD4BF;
            default -> 0xFF22D3EE;
        };
    }

    private static int qualityColor(int score, int threshold) {
        if (score >= 75) return 0xD9F43F5E;
        if (score >= 50) return 0xD9F59E0B;
        if (score >= threshold) return 0xD922D3EE;
        if (score >= 20) return 0xB08B5CF6;
        return 0x906B7280;
    }

    private static long key(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | (long) chunkZ << 32;
    }
}
