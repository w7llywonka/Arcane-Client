package dev.arcaneclient;

import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.ArcaneKeybinds;
import dev.arcaneclient.chat.ChatMacroController;
import dev.arcaneclient.combat.AutoTotemController;
import dev.arcaneclient.combat.CombatController;
import dev.arcaneclient.combat.CombatAutomationController;
import dev.arcaneclient.command.ArcaneCommands;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.render.EspRenderer;
import dev.arcaneclient.render.EntityEspRenderer;
import dev.arcaneclient.render.ItemEspRenderer;
import dev.arcaneclient.render.ActivityClusterLabelRenderer;
import dev.arcaneclient.render.ArcaneHud;
import dev.arcaneclient.render.TraceRenderer;
import dev.arcaneclient.render.TunnelEspRenderer;
import dev.arcaneclient.render.WorldIntelRenderer;
import dev.arcaneclient.render.WaypointStore;
import dev.arcaneclient.movement.MovementAssistController;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ArcaneWelcomeScreen;
import dev.arcaneclient.utility.AutoToolController;
import dev.arcaneclient.utility.AdvancedHudController;
import dev.arcaneclient.utility.ElytraAssistController;
import dev.arcaneclient.utility.QualityOfLifeController;
import dev.arcaneclient.utility.RelogController;
import dev.arcaneclient.utility.SafetyController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public final class ArcaneClient
implements ClientModInitializer {
    public static final String MOD_ID = "arcaneclient";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"arcaneclient");
    private static ArcaneConfig config;
    private static TraceEngine engine;
    private static ArcaneKeybinds keybinds;

    public void onInitializeClient() {
        config = ArcaneConfig.load();
        engine = new TraceEngine(config);
        keybinds = new ArcaneKeybinds();
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> {
            RelogController.onWorldChange(level);
            FreecamController.disable(client);
            FreelookController.disable(client);
            AutoToolController.restore(client);
            CombatController.reset(client);
            AutoTotemController.reset();
            CombatAutomationController.reset(client);
            ElytraAssistController.reset();
            QualityOfLifeController.onWorldChange(client, level);
            MovementAssistController.reset(client);
            EntityEspRenderer.reset();
            ItemEspRenderer.reset();
            ActivityClusterLabelRenderer.reset();
            WorldIntelRenderer.onWorldChange(level);
            SafetyController.reset();
            ArcaneHud.reset();
            AdvancedHudController.reset();
            engine.onWorldChange(client, level);
        });
        ClientChunkEvents.CHUNK_LOAD.register(engine::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(engine::onChunkUnload);
        ClientChunkEvents.CHUNK_LOAD.register(WorldIntelRenderer::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(WorldIntelRenderer::onChunkUnload);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (RelogController.tick(client)) {
                // Don't replay presses from the countdown after the new world loads.
                while (keybinds.relog().wasPressed()) { }
                return;
            }
            if (config.welcomeNoticeVersion < ArcaneWelcomeScreen.NOTICE_VERSION
                && client.currentScreen instanceof TitleScreen titleScreen) {
                config.welcomeNoticeVersion = ArcaneWelcomeScreen.NOTICE_VERSION;
                config.save();
                client.setScreen(new ArcaneWelcomeScreen(titleScreen));
                return;
            }
            while (keybinds.settings().wasPressed()) {
                client.setScreen((Screen)new ArcaneSettingsScreen(client.currentScreen));
            }
            while (keybinds.relog().wasPressed()) {
                RelogController.Result result = RelogController.relog(client);
                ArcaneClient.actionbar(client, result.message());
            }
            while (keybinds.scanner().wasPressed()) {
                boolean bl = ArcaneClient.config.enabled = !ArcaneClient.config.enabled;
                if (ArcaneClient.config.enabled) {
                    engine.queueNearby(client);
                }
                config.save();
                ArcaneClient.actionbar(client, "Arcane Client scanner " + (ArcaneClient.config.enabled ? "on" : "off"));
            }
            while (keybinds.overlay().wasPressed()) {
                ArcaneClient.config.overlay = !ArcaneClient.config.overlay;
                config.save();
                ArcaneClient.actionbar(client, "Arcane Client outlines " + (ArcaneClient.config.overlay ? "on" : "off"));
            }
            while (keybinds.freecam().wasPressed()) {
                FreecamController.toggle(client);
                ArcaneClient.actionbar(client, "Freecam " + (FreecamController.isActive() ? "on" : "off"));
            }
            while (keybinds.freelook().wasPressed()) {
                FreelookController.toggle(client);
                ArcaneClient.actionbar(client, "Freelook " + (FreelookController.isActive() ? "on" : "off"));
            }
            while (keybinds.cleanCapture().wasPressed()) {
                ArcaneClient.config.cleanCapture = !ArcaneClient.config.cleanCapture;
                config.save();
                ArcaneClient.actionbar(client, "Clean Capture " + (ArcaneClient.config.cleanCapture ? "on" : "off"));
            }
            while (keybinds.esp().wasPressed()) {
                ArcaneClient.config.esp = !ArcaneClient.config.esp;
                config.save();
                ArcaneClient.actionbar(client, "Storage ESP " + (ArcaneClient.config.esp ? "on" : "off"));
            }
            while (keybinds.autoTotem().wasPressed()) {
                ArcaneClient.config.autoTotem = !ArcaneClient.config.autoTotem;
                config.save();
                ArcaneClient.actionbar(client, "Auto Totem " + (ArcaneClient.config.autoTotem ? "on" : "off"));
            }
            while (keybinds.tunnelEsp().wasPressed()) {
                ArcaneClient.config.tunnelEsp = !ArcaneClient.config.tunnelEsp;
                engine.tunnelSettingsChanged(client);
                config.save();
                ArcaneClient.actionbar(client, "Tunnel ESP " + (ArcaneClient.config.tunnelEsp ? "on" : "off"));
            }
            while (keybinds.itemEsp().wasPressed()) {
                ArcaneClient.config.itemEsp = !ArcaneClient.config.itemEsp;
                config.save();
                ArcaneClient.actionbar(client, "Item ESP " + (ArcaneClient.config.itemEsp ? "on" : "off"));
            }
            while (keybinds.waypoint().wasPressed()) {
                if (client.player == null || client.world == null) continue;
                if (!config.waypoints) {
                    ArcaneClient.actionbar(client, "Enable Waypoints before adding a marker");
                    continue;
                }
                java.util.Set<String> names = WaypointStore.current(client).stream()
                    .map(point -> point.name().toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
                int next = 1;
                while (names.contains(("Waypoint " + next).toLowerCase(java.util.Locale.ROOT))) next++;
                WaypointStore.Waypoint waypoint = WaypointStore.add(client, "Waypoint " + next, client.player.getBlockPos());
                ArcaneClient.actionbar(client, "Saved " + waypoint.name());
            }
            ChatMacroController.tick(client);
            CombatController.tick(client);
            AutoTotemController.tick(client);
            ElytraAssistController.tick(client);
            CombatAutomationController.tick(client);
            QualityOfLifeController.tick(client);
            MovementAssistController.tick(client);
            SafetyController.tick(client);
            ArcaneHud.tick(client);
            AdvancedHudController.tick(client);
            FreecamController.tick(client);
            FreelookController.tick(client);
            EspRenderer.tick(client);
            EntityEspRenderer.tick(client);
            ItemEspRenderer.tick(client);
            WorldIntelRenderer.tick(client);
            engine.tick(client);
            TunnelEspRenderer.tick();
            ActivityClusterLabelRenderer.tick(client);
        });
        ArcaneCommands.register();
        CombatController.register();
        FreecamController.register();
        TraceRenderer.register();
        EspRenderer.register();
        EntityEspRenderer.register();
        ItemEspRenderer.register();
        TunnelEspRenderer.register();
        ActivityClusterLabelRenderer.register();
        WorldIntelRenderer.register();
        ArcaneHud.register();
        AdvancedHudController.register();
        LOGGER.info("Arcane Client initialized for Minecraft 1.21.11");
    }

    public static ArcaneConfig config() {
        return config;
    }

    public static TraceEngine engine() {
        return engine;
    }

    public static ArcaneKeybinds keybinds() {
        return keybinds;
    }

    public static Identifier id(String path) {
        return Identifier.of((String)MOD_ID, (String)path);
    }

    private static void actionbar(MinecraftClient client, String text) {
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage((Text)Text.literal((String)text), true);
        }
    }
}
