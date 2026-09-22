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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            RelogController.onWorldChange(level);
            FreecamController.disable(client);
            FreelookController.disable(client);
            AutoToolController.restore(client);
            CombatController.reset(client);
            AutoTotemController.reset();
            CombatAutomationController.reset(client);
            dev.arcaneclient.additions.combat.CombatAdditionsController.reset(client);
            dev.arcaneclient.additions.utility.UtilityAdditions.reset(client);
            dev.arcaneclient.additions.visual.VisualAdditions.reset(client);
            dev.arcaneclient.additions.intel.IntelAdditions.reset(client);
            dev.arcaneclient.additions.social.AutoTpa.reset(client);
            dev.arcaneclient.additions.media.MediaHud.reset(client);
            dev.arcaneclient.additions.preview.PreviewAdditions.reset(client);
            dev.arcaneclient.additions.dispenser.DispenserHelper.reset(client);
            dev.arcaneclient.additions.mining.MiningOverlay.reset(client);
            dev.arcaneclient.additions.effects.Effects.reset(client);
            dev.arcaneclient.additions.nuker.Nuker.reset(client);
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
            if (keybinds.migrateLegacyBindings()) {
                client.options.save();
                LOGGER.info("Migrated pre-26.3 Arcane key bindings to SDL scan codes");
            }
            if (RelogController.tick(client)) {
                // Don't replay presses from the countdown after the new world loads.
                while (keybinds.relog().consumeClick()) { }
                return;
            }
            if (config.welcomeNoticeVersion < ArcaneWelcomeScreen.NOTICE_VERSION
                && client.gui.screen() instanceof TitleScreen titleScreen) {
                config.welcomeNoticeVersion = ArcaneWelcomeScreen.NOTICE_VERSION;
                config.save();
                client.gui.setScreen(new ArcaneWelcomeScreen(titleScreen));
                return;
            }
            while (keybinds.settings().consumeClick()) {
                client.gui.setScreen((Screen)new ArcaneSettingsScreen(client.gui.screen()));
            }
            while (keybinds.relog().consumeClick()) {
                RelogController.Result result = RelogController.relog(client);
                ArcaneClient.actionbar(client, result.message());
            }
            while (keybinds.scanner().consumeClick()) {
                boolean bl = ArcaneClient.config.enabled = !ArcaneClient.config.enabled;
                if (ArcaneClient.config.enabled) {
                    engine.queueNearby(client);
                }
                config.save();
                ArcaneClient.actionbar(client, "Arcane Client scanner " + (ArcaneClient.config.enabled ? "on" : "off"));
            }
            while (keybinds.overlay().consumeClick()) {
                ArcaneClient.config.overlay = !ArcaneClient.config.overlay;
                config.save();
                ArcaneClient.actionbar(client, "Arcane Client outlines " + (ArcaneClient.config.overlay ? "on" : "off"));
            }
            while (keybinds.freecam().consumeClick()) {
                FreecamController.toggle(client);
                ArcaneClient.actionbar(client, "Freecam " + (FreecamController.isActive() ? "on" : "off"));
            }
            while (keybinds.freelook().consumeClick()) {
                FreelookController.toggle(client);
                ArcaneClient.actionbar(client, "Freelook " + (FreelookController.isActive() ? "on" : "off"));
            }
            while (keybinds.cleanCapture().consumeClick()) {
                ArcaneClient.config.cleanCapture = !ArcaneClient.config.cleanCapture;
                config.save();
                ArcaneClient.actionbar(client, "Clean Capture " + (ArcaneClient.config.cleanCapture ? "on" : "off"));
            }
            while (keybinds.esp().consumeClick()) {
                ArcaneClient.config.esp = !ArcaneClient.config.esp;
                config.save();
                ArcaneClient.actionbar(client, "Storage ESP " + (ArcaneClient.config.esp ? "on" : "off"));
            }
            while (keybinds.autoTotem().consumeClick()) {
                ArcaneClient.config.autoTotem = !ArcaneClient.config.autoTotem;
                config.save();
                ArcaneClient.actionbar(client, "Auto Totem " + (ArcaneClient.config.autoTotem ? "on" : "off"));
            }
            while (keybinds.tunnelEsp().consumeClick()) {
                ArcaneClient.config.tunnelEsp = !ArcaneClient.config.tunnelEsp;
                engine.tunnelSettingsChanged(client);
                config.save();
                ArcaneClient.actionbar(client, "Tunnel ESP " + (ArcaneClient.config.tunnelEsp ? "on" : "off"));
            }
            while (keybinds.itemEsp().consumeClick()) {
                ArcaneClient.config.itemEsp = !ArcaneClient.config.itemEsp;
                config.save();
                ArcaneClient.actionbar(client, "Item ESP " + (ArcaneClient.config.itemEsp ? "on" : "off"));
            }
            while (keybinds.waypoint().consumeClick()) {
                if (client.player == null || client.level == null) continue;
                if (!config.waypoints) {
                    ArcaneClient.actionbar(client, "Enable Waypoints before adding a marker");
                    continue;
                }
                java.util.Set<String> names = WaypointStore.current(client).stream()
                    .map(point -> point.name().toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
                int next = 1;
                while (names.contains(("Waypoint " + next).toLowerCase(java.util.Locale.ROOT))) next++;
                WaypointStore.Waypoint waypoint = WaypointStore.add(client, "Waypoint " + next, client.player.blockPosition());
                ArcaneClient.actionbar(client, "Saved " + waypoint.name());
            }
            ChatMacroController.tick(client);
            CombatController.tick(client);
            AutoTotemController.tick(client);
            ElytraAssistController.tick(client);
            CombatAutomationController.tick(client);
            dev.arcaneclient.additions.combat.CombatAdditionsController.tick(client);
            dev.arcaneclient.additions.utility.UtilityAdditions.tick(client);
            dev.arcaneclient.additions.visual.VisualAdditions.tick(client);
            dev.arcaneclient.additions.intel.IntelAdditions.tick(client);
            dev.arcaneclient.additions.social.AutoTpa.tick(client);
            dev.arcaneclient.additions.media.MediaHud.tick(client);
            dev.arcaneclient.additions.preview.PreviewAdditions.tick(client);
            dev.arcaneclient.additions.dispenser.DispenserHelper.tick(client);
            dev.arcaneclient.additions.mining.MiningOverlay.tick(client);
            dev.arcaneclient.additions.presence.ArcanePresence.tick(client);
            dev.arcaneclient.additions.effects.Effects.tick(client);
            dev.arcaneclient.additions.nuker.Nuker.tick(client);
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
        dev.arcaneclient.additions.visual.VisualAdditions.register();
        dev.arcaneclient.additions.intel.IntelAdditions.register();
        dev.arcaneclient.additions.social.AutoTpa.register();
        dev.arcaneclient.additions.media.MediaHud.register();
        dev.arcaneclient.additions.preview.PreviewAdditions.register();
        dev.arcaneclient.additions.dispenser.DispenserHelper.register();
        dev.arcaneclient.additions.mining.MiningOverlay.register();
        dev.arcaneclient.additions.presence.ArcanePresence.register();
        dev.arcaneclient.additions.effects.Effects.register();
        dev.arcaneclient.additions.susfinder.SusChunkFinderController.register(() -> config.susFinder);
        dev.arcaneclient.additions.configlibrary.ConfigLibrary.registerApplyHook(ArcaneClient::onProfileApplied);
        LOGGER.info("Arcane Client initialized for Minecraft 26.3");
    }

    private static void onProfileApplied(Minecraft client) {
        FreecamController.disable(client);
        FreelookController.disable(client);
        AutoToolController.restore(client);
        CombatController.reset(client);
        AutoTotemController.reset();
        CombatAutomationController.reset(client);
        dev.arcaneclient.additions.combat.CombatAdditionsController.reset(client);
        dev.arcaneclient.additions.utility.UtilityAdditions.reset(client);
        dev.arcaneclient.additions.visual.VisualAdditions.reset(client);
        dev.arcaneclient.additions.intel.IntelAdditions.reset(client);
        dev.arcaneclient.additions.social.AutoTpa.reset(client);
        dev.arcaneclient.additions.media.MediaHud.reset(client);
        dev.arcaneclient.additions.preview.PreviewAdditions.reset(client);
        dev.arcaneclient.additions.dispenser.DispenserHelper.reset(client);
        dev.arcaneclient.additions.mining.MiningOverlay.reset(client);
        dev.arcaneclient.additions.presence.ArcanePresence.stop();
        dev.arcaneclient.additions.effects.Effects.reset(client);
        dev.arcaneclient.additions.nuker.Nuker.reset(client);
        if (dev.arcaneclient.additions.susfinder.SusChunkFinderController.instance() != null)
            dev.arcaneclient.additions.susfinder.SusChunkFinderController.instance().reset();
        dev.arcaneclient.inventory.InventoryActionScheduler.shared().reset();
        ElytraAssistController.reset();
        MovementAssistController.reset(client);
        EntityEspRenderer.reset();
        ItemEspRenderer.reset();
        ActivityClusterLabelRenderer.reset();
        WorldIntelRenderer.onWorldChange(client.level);
        SafetyController.reset();
        ArcaneHud.reset();
        AdvancedHudController.reset();
        engine.onWorldChange(client, client.level);
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
        return Identifier.fromNamespaceAndPath((String)MOD_ID, (String)path);
    }

    private static void actionbar(Minecraft client, String text) {
        LocalPlayer player = client.player;
        if (player != null) {
            player.sendOverlayMessage((Component)Component.literal((String)text));
        }
    }
}
