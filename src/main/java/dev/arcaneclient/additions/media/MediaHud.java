package dev.arcaneclient.additions.media;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ClickGuiColors;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import dev.arcaneclient.screen.RoundedGui;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Opt-in local Spotify HUD. Metadata exists only in memory while the module is active. */
@Environment(EnvType.CLIENT)
public final class MediaHud {
    private static final SpotifyBridge BRIDGE = new SpotifyBridge();
    private static final String[] ANCHORS = {"Bottom left", "Bottom right", "Top left", "Top right"};
    private static boolean registered;

    private MediaHud() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        MediaHudConfig c = config.mediaHud;
        return List.of(GuiModule.toggle("Spotify HUD",
                "Reads Spotify title, artist and playback from local Windows media sessions when enabled. No network requests or Spotify tokens.",
                () -> c.enabled, value -> {
                    c.enabled = value;
                    if (!value) BRIDGE.setEnabled(false);
                })
            .with(new GuiSetting.Cycle("Anchor", () -> ANCHORS[Math.clamp(c.anchor, 0, 3)], () -> c.anchor = (c.anchor + 1) % 4))
            .with(new GuiSetting.Slider("Horizontal margin", () -> c.offsetX, value -> c.offsetX = value, 0, 400, " px"))
            .with(new GuiSetting.Slider("Vertical margin", () -> c.offsetY, value -> c.offsetY = value, 0, 400, " px"))
            .with(new GuiSetting.Slider("Width", () -> c.width, value -> c.width = value, 170, 320, " px"))
            .with(new GuiSetting.Toggle("Progress", () -> c.progress, value -> c.progress = value))
            .with(new GuiSetting.Toggle("Hide paused", () -> c.hidePaused, value -> c.hidePaused = value))
            .with(new GuiSetting.Info("Status", () -> status(client, config)))
            .build());
    }

    public static void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ArcaneClient.id("spotify_media"),
            (graphics, tickCounter) -> render(graphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BRIDGE.shutdown());
        BRIDGE.registerShutdownHook();
    }

    public static void tick(Minecraft client) {
        ArcaneConfig c = ArcaneClient.config();
        BRIDGE.setEnabled(c != null && c.mediaHud.enabled && client.level != null && client.player != null
            && !c.streamerMode && !ArcaneVisibility.overlaysHidden());
    }

    public static void reset(Minecraft client) {
        BRIDGE.setEnabled(false);
    }

    private static String status(Minecraft client, ArcaneConfig config) {
        if (!config.mediaHud.enabled) return "Disabled";
        if (!SpotifyBridge.supported()) return "Windows 10/11 only";
        if (config.streamerMode || ArcaneVisibility.overlaysHidden()) return "Hidden for privacy";
        if (client.level == null || client.player == null) return "Join a world to connect";
        return BRIDGE.status();
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.mediaHud.enabled || config.streamerMode || ArcaneVisibility.overlaysHidden()
            || client.level == null || client.player == null || client.gui.hud.isHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        MediaHudConfig c = config.mediaHud;
        SpotifyBridge.Snapshot track = BRIDGE.snapshot();
        if (!track.displayable() || (c.hidePaused && track.paused())) return;
        Font font = ArcaneFont.renderer(client);
        ClickGuiColors theme = ClickGuiColors.resolve(config);
        int availableWidth = graphics.guiWidth();
        int availableHeight = graphics.guiHeight();
        int width = Math.min(c.width, availableWidth - 16);
        boolean progress = c.progress && track.durationMs() > 0;
        int height = progress ? 65 : 48;
        if (width < 120 || availableHeight < height + 16) return;
        boolean right = c.anchor == 1 || c.anchor == 3;
        boolean top = c.anchor >= 2;
        int x = Math.clamp(right ? availableWidth - width - c.offsetX : c.offsetX, 8, availableWidth - width - 8);
        int y = Math.clamp(top ? c.offsetY : availableHeight - height - c.offsetY, 8, availableHeight - height - 8);
        RoundedGui.fill(graphics, x + 1, y + 2, width, height, 8, 0x40000000);
        RoundedGui.outline(graphics, x, y, width, height, 8, 1, theme.outline(), theme.window());
        int accent = track.playing() ? 0xFF65CD93 : theme.muted();
        RoundedGui.fill(graphics, x + 10, y + 8, 3, 7, 1, accent);
        RoundedGui.fill(graphics, x + 15, y + (track.playing() ? 5 : 8), 3, track.playing() ? 10 : 7, 1, accent);
        graphics.text(font, ArcaneFont.text("SPOTIFY"), x + 24, y + 6, accent, false);
        String state = track.playing() ? "PLAYING" : "PAUSED";
        graphics.text(font, ArcaneFont.text(state), x + width - 10 - ArcaneFont.width(font, state), y + 6, theme.faint(), false);
        graphics.text(font, ArcaneFont.trimmed(font, track.title(), width - 20), x + 10, y + 20, theme.text(), false);
        if (!track.artist().isBlank()) {
            graphics.text(font, ArcaneFont.trimmed(font, track.artist(), width - 20), x + 10, y + 32, theme.muted(), false);
        }
        if (progress) {
            long position = track.currentPositionMs();
            String time = time(position) + " / " + time(track.durationMs());
            graphics.text(font, ArcaneFont.text(time), x + 10, y + 45, theme.faint(), false);
            RoundedGui.fill(graphics, x + 10, y + 58, width - 20, 2, 1, theme.outline());
            int filled = Math.round((width - 20) * Math.clamp(position / (float)track.durationMs(), 0.0f, 1.0f));
            RoundedGui.fill(graphics, x + 10, y + 58, filled, 2, 1, accent);
        }
    }

    private static String time(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }
}
