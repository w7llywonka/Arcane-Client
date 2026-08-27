package dev.arcaneclient;

import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.ArcaneKeybinds;
import dev.arcaneclient.chat.ChatMacroController;
import dev.arcaneclient.combat.AutoTotemController;
import dev.arcaneclient.command.ArcaneCommands;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.EspRenderer;
import dev.arcaneclient.render.ItemEspRenderer;
import dev.arcaneclient.render.GrowthLabelRenderer;
import dev.arcaneclient.render.ArcaneHud;
import dev.arcaneclient.render.TraceRenderer;
import dev.arcaneclient.render.TunnelEspRenderer;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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
            FreecamController.disable(client);
            engine.onWorldChange(client, level);
        });
        ClientChunkEvents.CHUNK_LOAD.register(engine::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(engine::onChunkUnload);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keybinds.settings().wasPressed()) {
                client.setScreen((Screen)new ArcaneSettingsScreen(client.currentScreen));
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
            ChatMacroController.tick(client);
            AutoTotemController.tick(client);
            FreecamController.tick(client);
            EspRenderer.tick(client);
            ItemEspRenderer.tick(client);
            engine.tick(client);
            TunnelEspRenderer.tick();
            GrowthLabelRenderer.tick(client);
        });
        ArcaneCommands.register();
        FreecamController.register();
        TraceRenderer.register();
        EspRenderer.register();
        ItemEspRenderer.register();
        TunnelEspRenderer.register();
        GrowthLabelRenderer.register();
        ArcaneHud.register();
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
