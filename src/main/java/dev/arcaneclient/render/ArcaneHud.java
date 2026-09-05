package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.combat.CombatController;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ClickGuiColors;
import dev.arcaneclient.screen.RoundedGui;
import dev.arcaneclient.screen.UiGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
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
    private static final int PADDING = 4;
    private static final int CLUSTER_COLOR = 0xFFF08AA0;

    private static Map<Long, TraceEngine.ChunkMarker> cachedMarkers = Map.of();
    private static long cachedTickBucket = Long.MIN_VALUE;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static Set<Long> cachedClusterChunks = Set.of();
    private static List<String> cachedInfoLines = List.of();
    private static int infoRefreshIn;
    private static boolean cachedInfoStreamerMode;

    private ArcaneHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, (graphics, tickCounter) -> render(graphics));
    }

    public static void reset() {
        cachedMarkers = Map.of();
        cachedTickBucket = Long.MIN_VALUE;
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedClusterChunks = Set.of();
        cachedInfoLines = List.of();
        infoRefreshIn = 0;
        cachedInfoStreamerMode = false;
    }

    /** Refreshes optional telemetry at 4 Hz instead of querying and formatting it every frame. */
    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        if (client.player == null || client.world == null || !config.infoHud) {
            cachedInfoLines = List.of();
            infoRefreshIn = 0;
            return;
        }
        if (cachedInfoStreamerMode == config.streamerMode && infoRefreshIn > 0) {
            infoRefreshIn--;
            return;
        }
        cachedInfoLines = buildInfoLines(client, config);
        cachedInfoStreamerMode = config.streamerMode;
        infoRefreshIn = 4;
    }

    private static void render(DrawContext graphics) {
        ArcaneConfig config = ArcaneClient.config();
        MinecraftClient client = MinecraftClient.getInstance();
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client) || client.player == null || client.world == null) return;
        if (config.hud) renderRadar(graphics, client, config);
        if (config.chunkAnalysis) renderChunkAnalysis(graphics, client, config);
        if (config.attackMeter) renderCombatHud(graphics, client, config);
        if (config.infoHud) renderInfoHud(graphics, client, config);
        if (config.customCrosshair) renderCustomCrosshair(graphics, client, config);
    }

    private static void renderRadar(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        TraceEngine engine = ArcaneClient.engine();
        ChunkPos center = client.player.getChunkPos();
        Map<Long, TraceEngine.ChunkMarker> markers = nearbyMarkers(engine, center);
        ClickGuiColors theme = ClickGuiColors.resolve(config);
        int cellSize = UiGeometry.radarCellSize(ArcaneFont.width(font, "99"));
        int gridPixels = GRID_SIZE * cellSize;
        int panelWidth = gridPixels + PADDING * 2;
        int panelHeight = gridPixels + PADDING * 2;

        drawGlassPanel(graphics, PANEL_X, PANEL_Y, panelWidth, panelHeight, theme);

        int gridX = PANEL_X + PADDING;
        int gridY = PANEL_Y + PADDING;
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = gridX + (dx + RADIUS) * cellSize;
                int y = gridY + (dz + RADIUS) * cellSize;
                long chunkKey = key(center.x + dx, center.z + dz);
                TraceEngine.ChunkMarker marker = markers.get(chunkKey);
                boolean clustered = cachedClusterChunks.contains(chunkKey);
                int score = marker == null ? 0 : marker.score();
                int tile = score == 0 ? 0x5A1C1E24 : qualityColor(score, config.threshold);
                if (dx == 0 && dz == 0 || clustered) {
                    RoundedGui.outline(graphics, x, y, cellSize, cellSize, 4, 1, clustered ? CLUSTER_COLOR : theme.accent(), tile);
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
        ClickGuiColors theme = ClickGuiColors.resolve(config);
        int panelWidth = 184;
        int x = graphics.getScaledWindowWidth() - panelWidth - 7;
        int y = 7;
        int reasonCount = marker == null ? 0 : Math.min(2, marker.reasons().size());
        int height = marker == null || marker.score() == 0 ? 45 : 58 + reasonCount * 10;

        drawGlassPanel(graphics, x, y, panelWidth, height, theme);
        RoundedGui.fill(graphics, x + 8, y + 2, 28, 2, 1, theme.accent());
        graphics.drawText(font, ArcaneFont.text("CURRENT CHUNK"), x + 9, y + 6, theme.text(), false);
        String coordinates = config.streamerMode ? "COORDINATES HIDDEN" : chunk.x + ", " + chunk.z;
        graphics.drawText(font, ArcaneFont.text(coordinates), x + 9, y + 19, theme.accentBright(), false);

        if (marker == null || marker.score() == 0) {
            OrderedText empty = ArcaneFont.trimmed(font, "No activity evidence", panelWidth - 18);
            graphics.drawText(font, empty, x + 9, y + 32, theme.muted(), false);
            return;
        }

        TraceEngine.ActivityClusterCandidate cluster = activityClusterAt(engine, chunk);
        String score = cluster == null
            ? "SCORE  " + marker.score()
            : "BASE " + cluster.confidence() + "% · " + cluster.members() + " CH";
        int scoreX = x + panelWidth - 9 - ArcaneFont.width(font, score);
        graphics.drawText(font, ArcaneFont.text(score), scoreX, y + 6, cluster != null ? CLUSTER_COLOR : theme.accent(), false);

        var intel = marker.intel();
        String confidence = "CONF " + intel.confidence() + "%  FAM " + intel.independentFamilies()
            + "  SAMPLES " + intel.completeSamples();
        String freshness = "FRESH " + intel.freshness() + "%  COVER " + intel.coveragePercent() + "%";
        graphics.drawText(font, ArcaneFont.trimmed(font, confidence, panelWidth - 18), x + 9, y + 31, theme.accentBright(), false);
        graphics.drawText(font, ArcaneFont.trimmed(font, freshness, panelWidth - 18), x + 9, y + 42, theme.muted(), false);

        int lineY = y + 55;
        for (int index = 0; index < reasonCount; index++) {
            OrderedText reason = ArcaneFont.trimmed(font, "› " + marker.reasons().get(index), panelWidth - 22);
            graphics.drawText(font, reason, x + 9, lineY, theme.text(), false);
            lineY += 10;
        }
    }

    private static void renderCombatHud(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        ClickGuiColors theme = ClickGuiColors.resolve(config);
        int width = 64;
        int x = (graphics.getScaledWindowWidth() - width) / 2;
        int y = graphics.getScaledWindowHeight() / 2 + 13;
        float cooldown = Math.clamp(client.player.getAttackCooldownProgress(0.0f), 0.0f, 1.0f);
        RoundedGui.fill(graphics, x, y, width, 5, 2, theme.window());
        RoundedGui.fill(graphics, x + 1, y + 1, Math.round((width - 2) * cooldown), 3, 1, cooldown >= 0.95f ? theme.accentBright() : theme.accent());
        if (config.totemCounter) {
            String count = "TOTEMS " + CombatController.totemCount(client.player);
            graphics.drawText(font, ArcaneFont.text(count), x + (width - ArcaneFont.width(font, count)) / 2, y + 7, theme.text(), false);
        }
    }

    private static void renderInfoHud(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        TextRenderer font = ArcaneFont.renderer(client);
        ClickGuiColors theme = ClickGuiColors.resolve(config);
        List<String> lines = cachedInfoLines;
        if (lines.isEmpty() || cachedInfoStreamerMode != config.streamerMode) return;

        int panelWidth = 80;
        for (String line : lines) panelWidth = Math.max(panelWidth, ArcaneFont.width(font, line) + 16);
        int lineHeight = font.fontHeight + 3;
        int panelHeight = lines.size() * lineHeight + 9;
        int x = 7;
        int y = graphics.getScaledWindowHeight() - panelHeight - 7;
        drawGlassPanel(graphics, x, y, panelWidth, panelHeight, theme);
        RoundedGui.fill(graphics, x + 8, y + 2, 28, 2, 1, theme.accent());
        int textY = y + 6;
        for (String line : lines) {
            graphics.drawText(font, ArcaneFont.text(line), x + 8, textY, theme.text(), false);
            textY += lineHeight;
        }
    }

    private static List<String> buildInfoLines(MinecraftClient client, ArcaneConfig config) {
        List<String> lines = new ArrayList<>(6);
        if (config.infoFps) lines.add("FPS  " + client.getCurrentFps());
        if (config.infoCoordinates) {
            lines.add(config.streamerMode
                ? "XYZ  HIDDEN"
                : InfoHudFormatter.coordinates(client.player.getX(), client.player.getY(), client.player.getZ()));
        }
        if (config.infoDirection) {
            lines.add("FACING  " + client.player.getHorizontalFacing().asString().toUpperCase(Locale.ROOT));
        }
        if (config.infoSpeed) {
            lines.add(InfoHudFormatter.speed(client.player.getVelocity().x, client.player.getVelocity().z));
        }
        if (config.infoPing) {
            PlayerListEntry entry = client.getNetworkHandler() == null
                ? null
                : client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            lines.add("PING  " + (entry == null ? "--" : entry.getLatency() + " ms"));
        }
        if (config.infoBiome) {
            String biome = client.world.getBiome(client.player.getBlockPos()).getKey()
                .map(key -> InfoHudFormatter.readableId(key.getValue().getPath()))
                .orElse("UNKNOWN");
            lines.add("BIOME  " + biome);
        }
        return List.copyOf(lines);
    }

    private static void renderCustomCrosshair(DrawContext graphics, MinecraftClient client, ArcaneConfig config) {
        int centerX = graphics.getScaledWindowWidth() / 2;
        int centerY = graphics.getScaledWindowHeight() / 2;
        int size = config.crosshairSize;
        double horizontalSpeed = Math.hypot(client.player.getVelocity().x, client.player.getVelocity().z);
        int dynamicSpread = client.player.isOnGround() ? (int)Math.min(3, Math.round(horizontalSpeed * 10.0)) : 3;
        int gap = config.crosshairGap + dynamicSpread;
        float cooldown = client.player.getAttackCooldownProgress(0.0f);
        int configured = config.crosshairColor;
        int color = cooldown >= 0.95f
            ? configured
            : 0xA0000000 | configured & 0x00FFFFFF;
        graphics.fill(centerX - gap - size, centerY, centerX - gap, centerY + 1, color);
        graphics.fill(centerX + gap + 1, centerY, centerX + gap + size + 1, centerY + 1, color);
        graphics.fill(centerX, centerY - gap - size, centerX + 1, centerY - gap, color);
        graphics.fill(centerX, centerY + gap + 1, centerX + 1, centerY + gap + size + 1, color);
    }

    private static void drawGlassPanel(
        DrawContext graphics,
        int x,
        int y,
        int width,
        int height,
        ClickGuiColors theme
    ) {
        RoundedGui.fill(graphics, x + 2, y + 3, width, height, 7, 0x52000000);
        RoundedGui.fill(graphics, x, y, width, height, 7, theme.window());
        RoundedGui.fill(graphics, x + 11, y + 1, width - 22, 1, 1, theme.outlineSoft());
    }

    private static TraceEngine.ActivityClusterCandidate activityClusterAt(TraceEngine engine, ChunkPos chunk) {
        for (TraceEngine.ActivityClusterCandidate candidate : engine.activityClusters()) {
            if (candidate.contains(chunk.x, chunk.z)) return candidate;
        }
        return null;
    }

    private static Map<Long, TraceEngine.ChunkMarker> nearbyMarkers(TraceEngine engine, ChunkPos center) {
        long tickBucket = engine.currentTick() / 10L;
        if (tickBucket == cachedTickBucket && center.x == cachedCenterX && center.z == cachedCenterZ) return cachedMarkers;
        HashMap<Long, TraceEngine.ChunkMarker> refreshed = new HashMap<>();
        for (TraceEngine.ChunkMarker marker : engine.nearby(center.x, center.z, RADIUS)) {
            refreshed.put(key(marker.chunkX(), marker.chunkZ()), marker);
        }
        HashSet<Long> clusterChunks = new HashSet<>();
        for (TraceEngine.ActivityClusterCandidate cluster : engine.activityClusters()) {
            for (long member : cluster.memberChunks()) {
                int chunkX = (int)member;
                int chunkZ = (int)(member >> 32);
                if (Math.abs(chunkX - center.x) <= RADIUS && Math.abs(chunkZ - center.z) <= RADIUS) {
                    clusterChunks.add(member);
                }
            }
        }
        cachedMarkers = Map.copyOf(refreshed);
        cachedClusterChunks = Set.copyOf(clusterChunks);
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
