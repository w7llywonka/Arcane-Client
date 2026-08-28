package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.RoundedGui;
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
    private static final int CELL_SIZE = 11;
    private static final int PANEL_X = 7;
    private static final int PANEL_Y = 7;
    private static final int HEADER_HEIGHT = 17;
    private static final int PADDING = 4;

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

        RoundedGui.fill(graphics, PANEL_X + 2, PANEL_Y + 3, panelWidth, panelHeight, 7, 0x40000000);
        RoundedGui.outline(graphics, PANEL_X, PANEL_Y, panelWidth, panelHeight, 7, 1, 0xB83A3C45, 0xD90D0E11);
        RoundedGui.fill(graphics, PANEL_X + 8, PANEL_Y + 2, 28, 2, 1, accent);

        String state = config.enabled ? "ON" : "PAUSED";
        String header = "ARCANE  " + state + (FreecamController.isActive() ? "  FC" : "") + (config.esp ? "  ESP" : "");
        graphics.drawText(font, header, PANEL_X + PADDING, PANEL_Y + 5, config.enabled ? 0xFFF4F4F5 : 0xFFF08AA0, false);
        String flagged = Integer.toString(engine.flaggedCount());
        graphics.drawText(font, flagged, PANEL_X + panelWidth - PADDING - font.getWidth(flagged), PANEL_Y + 5, secondary, false);

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
                int tile = score == 0 ? 0x5A1C1E24 : qualityColor(score, config.threshold);
                if (dx == 0 && dz == 0) {
                    RoundedGui.outline(graphics, x, y, CELL_SIZE, CELL_SIZE, 3, 1, accent, tile);
                } else {
                    RoundedGui.fill(graphics, x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2, 2, tile);
                }
                if (score > 0) {
                    int scoreColor = score >= config.threshold && score < 75 ? 0xFF16171B : 0xFFF4F4F5;
                    graphics.drawCenteredTextWithShadow(font, Integer.toString(Math.min(99, score)), x + CELL_SIZE / 2, y + 2, scoreColor);
                } else if (dx == 0 && dz == 0) {
                    graphics.drawCenteredTextWithShadow(font, "+", x + CELL_SIZE / 2, y + 2, secondary);
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
        int panelWidth = 184;
        int x = graphics.getScaledWindowWidth() - panelWidth - 7;
        int y = 7;
        int reasonCount = marker == null ? 0 : Math.min(3, marker.reasons().size());
        int height = marker == null || marker.score() == 0 ? 45 : 37 + reasonCount * 10;

        RoundedGui.fill(graphics, x + 2, y + 3, panelWidth, height, 7, 0x40000000);
        RoundedGui.outline(graphics, x, y, panelWidth, height, 7, 1, 0xB83A3C45, 0xD90D0E11);
        RoundedGui.fill(graphics, x + 8, y + 2, 28, 2, 1, accent);
        graphics.drawText(font, "CURRENT CHUNK", x + 9, y + 6, 0xFFF4F4F5, false);
        graphics.drawText(font, chunk.x + ", " + chunk.z, x + 9, y + 19, secondary, false);

        if (marker == null || marker.score() == 0) {
            String empty = font.trimToWidth("No activity evidence", panelWidth - 18);
            graphics.drawText(font, empty, x + 9, y + 32, 0xFFA1A1AA, false);
            return;
        }

        boolean stash = isStashChunk(engine, chunk);
        String score = stash ? "POSSIBLE STASH  " + marker.score() : "SCORE  " + marker.score();
        int scoreX = x + panelWidth - 9 - font.getWidth(score);
        graphics.drawText(font, score, scoreX, y + 6, stash ? 0xFFF08AA0 : accent, false);

        int lineY = y + 31;
        for (int index = 0; index < reasonCount; index++) {
            String reason = font.trimToWidth(marker.reasons().get(index), panelWidth - 25);
            graphics.drawText(font, "› " + reason, x + 9, lineY, 0xFFD4D4D8, false);
            lineY += 10;
        }
    }

    private static boolean isStashChunk(TraceEngine engine, ChunkPos chunk) {
        for (TraceEngine.StashCandidate candidate : engine.stashCandidates()) {
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
            case 1 -> 0xFF76A9FF;
            case 2 -> 0xFFF08AA0;
            default -> 0xFF9A8CFF;
        };
    }

    private static int secondary(int theme) {
        return switch (Math.floorMod(theme, 3)) {
            case 1 -> 0xFFB8D0FF;
            case 2 -> 0xFFF6B4C1;
            default -> 0xFFC3BCFF;
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
