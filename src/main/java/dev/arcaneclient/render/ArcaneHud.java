package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ClickGuiTheme;
import dev.arcaneclient.screen.RoundedGui;
import dev.arcaneclient.screen.UiGeometry;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

@Environment(EnvType.CLIENT)
public final class ArcaneHud {
    private static final Identifier HUD_ID = ArcaneClient.id("nearby_chunks");
    private static final int RADIUS = 3;
    private static final int GRID_SIZE = 7;
    private static final int PANEL_X = 7;
    private static final int PANEL_Y = 7;
    private static final int HEADER_HEIGHT = 17;
    private static final int PADDING = 4;
    private static final int PAUSED_COLOR = 0xFFF08AA0;
    private static final int STASH_COLOR = 0xFFF08AA0;

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
        if (ArcaneSettingsScreen.isOpen(client) || client.player == null) return;
        if (config.hud) renderRadar(graphics, client, config);
        if (config.chunkAnalysis) renderChunkAnalysis(graphics, client, config);
    }

    private static void renderRadar(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        TraceEngine engine = ArcaneClient.engine();
        ChunkPos center = client.player.getChunkPos();
        Map<Long, TraceEngine.ChunkMarker> markers = nearbyMarkers(engine, center);
        ClickGuiTheme theme = ClickGuiTheme.fromConfig(config.uiTheme);
        int cellSize = UiGeometry.radarCellSize(ArcaneFont.width(font, "99"));
        int gridPixels = GRID_SIZE * cellSize;
        int panelWidth = gridPixels + PADDING * 2;
        int panelHeight = HEADER_HEIGHT + gridPixels + PADDING;

        RoundedGui.fill(graphics, PANEL_X + 2, PANEL_Y + 3, panelWidth, panelHeight, 7, 0x40000000);
        RoundedGui.outline(graphics, PANEL_X, PANEL_Y, panelWidth, panelHeight, 7, 1, theme.outlineSoft(), theme.window());
        RoundedGui.fill(graphics, PANEL_X + 8, PANEL_Y + 2, 28, 2, 1, theme.accent());

        String state = config.enabled ? "ON" : "PAUSED";
        String header = "ARCANE  " + state + (FreecamController.isActive() ? "  FC" : "") + (config.esp ? "  ESP" : "");
        graphics.drawText(font, ArcaneFont.text(header), PANEL_X + PADDING, PANEL_Y + 5, config.enabled ? theme.text() : PAUSED_COLOR, false);
        String flagged = Integer.toString(engine.flaggedCount());
        graphics.drawText(font, ArcaneFont.text(flagged), PANEL_X + panelWidth - PADDING - ArcaneFont.width(font, flagged), PANEL_Y + 5, theme.accentBright(), false);

        int gridX = PANEL_X + PADDING;
        int gridY = PANEL_Y + HEADER_HEIGHT;
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = gridX + (dx + RADIUS) * cellSize;
                int y = gridY + (dz + RADIUS) * cellSize;
                TraceEngine.ChunkMarker marker = markers.get(key(center.x + dx, center.z + dz));
                int score = marker == null ? 0 : marker.score();
                int tile = score == 0 ? 0x5A1C1E24 : qualityColor(score, config.threshold);
                if (dx == 0 && dz == 0) {
                    RoundedGui.outline(graphics, x, y, cellSize, cellSize, 4, 1, theme.accent(), tile);
                } else {
                    RoundedGui.fill(graphics, x + 1, y + 1, cellSize - 2, cellSize - 2, 3, tile);
                }
                if (score > 0) {
                    int scoreColor = score >= config.threshold && score < 75 ? 0xFF16171B : theme.text();
                    String scoreText = Integer.toString(Math.min(99, score));
                    int scoreX = x + (cellSize - ArcaneFont.width(font, scoreText)) / 2;
                    int scoreY = y + (cellSize - font.fontHeight) / 2;
                    graphics.drawText(font, ArcaneFont.text(scoreText), scoreX, scoreY, scoreColor, false);
                } else if (dx == 0 && dz == 0) {
                    int plusX = x + (cellSize - ArcaneFont.width(font, "+")) / 2;
                    int plusY = y + (cellSize - font.fontHeight) / 2;
                    graphics.drawText(font, ArcaneFont.text("+"), plusX, plusY, theme.accentBright(), false);
                }
            }
        }
    }

    private static void renderChunkAnalysis(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        TraceEngine engine = ArcaneClient.engine();
        ChunkPos chunk = client.player.getChunkPos();
        TraceEngine.ChunkMarker marker = engine.snapshotMarkerAt(chunk.x, chunk.z);
        ClickGuiTheme theme = ClickGuiTheme.fromConfig(config.uiTheme);
        int panelWidth = 184;
        int x = graphics.getScaledWindowWidth() - panelWidth - 7;
        int y = 7;
        int reasonCount = marker == null ? 0 : Math.min(3, marker.reasons().size());
        int height = marker == null || marker.score() == 0 ? 45 : 37 + reasonCount * 10;

        RoundedGui.fill(graphics, x + 2, y + 3, panelWidth, height, 7, 0x40000000);
        RoundedGui.outline(graphics, x, y, panelWidth, height, 7, 1, theme.outlineSoft(), theme.window());
        RoundedGui.fill(graphics, x + 8, y + 2, 28, 2, 1, theme.accent());
        graphics.drawText(font, ArcaneFont.text("CURRENT CHUNK"), x + 9, y + 6, theme.text(), false);
        graphics.drawText(font, ArcaneFont.text(chunk.x + ", " + chunk.z), x + 9, y + 19, theme.accentBright(), false);

        if (marker == null || marker.score() == 0) {
            OrderedText empty = ArcaneFont.trimmed(font, "No activity evidence", panelWidth - 18);
            graphics.drawText(font, empty, x + 9, y + 32, theme.muted(), false);
            return;
        }

        boolean stash = isStashChunk(engine, chunk);
        String score = stash ? "POSSIBLE STASH  " + marker.score() : "SCORE  " + marker.score();
        int scoreX = x + panelWidth - 9 - ArcaneFont.width(font, score);
        graphics.drawText(font, ArcaneFont.text(score), scoreX, y + 6, stash ? STASH_COLOR : theme.accent(), false);

        int lineY = y + 31;
        for (int index = 0; index < reasonCount; index++) {
            OrderedText reason = ArcaneFont.trimmed(font, "› " + marker.reasons().get(index), panelWidth - 22);
            graphics.drawText(font, reason, x + 9, lineY, theme.text(), false);
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
        if (tickBucket == cachedTickBucket && center.x == cachedCenterX && center.z == cachedCenterZ) return cachedMarkers;
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

    private static int qualityColor(int score, int threshold) {
        if (score >= 75) return 0xD9F43F5E;
        if (score >= 50) return 0xD9F59E0B;
        if (score >= threshold) return 0xD922D3EE;
        if (score >= 20) return 0xB08B5CF6;
        return 0x906B7280;
    }

    private static long key(int chunkX, int chunkZ) {
        return (long)chunkX & 0xFFFFFFFFL | (long)chunkZ << 32;
    }
}