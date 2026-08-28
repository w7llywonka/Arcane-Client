package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.ArcaneKeybinds;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.EspRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Builds the category windows shown by the Click GUI. Arcane is a base-finding client, so discovery
 * leads the list and every other category exists to support a scan.
 */
@Environment(EnvType.CLIENT)
public final class ModuleCatalog {
    private ModuleCatalog() {
    }

    public static List<GuiCategory> build(ArcaneConfig config, MinecraftClient client) {
        ArcaneKeybinds keybinds = ArcaneClient.keybinds();
        List<GuiCategory> categories = new ArrayList<>();
        categories.add(new GuiCategory("BASE FINDING", baseFinding(config, keybinds)));
        categories.add(new GuiCategory("ESP", esp(config, keybinds, client)));
        categories.add(new GuiCategory("RENDER", render(config, keybinds, client)));
        categories.add(new GuiCategory("UTILITY", utility(config, keybinds)));
        categories.add(new GuiCategory("CLIENT", clientTools(config, keybinds, client)));
        return List.copyOf(categories);
    }

    private static List<GuiModule> baseFinding(ArcaneConfig config, ArcaneKeybinds keybinds) {
        GuiModule chunkFinder = GuiModule
            .toggle(
                "Chunk Finder",
                "Scores every loaded chunk from farm, machine and player-block evidence so hidden bases surface while you fly.",
                () -> config.enabled,
                value -> config.enabled = value
            )
            .with(new GuiSetting.Slider("Sensitivity", config::sensitivity, config::setSensitivity, 0, 100, "%"))
            .with(new GuiSetting.Slider("Scan radius", () -> config.scanRadius, value -> config.scanRadius = value, 2, 24, " ch"))
            .with(new GuiSetting.Slider("Scan speed", () -> config.chunksPerTick, value -> config.chunksPerTick = value, 1, 8, "/t"))
            .with(new GuiSetting.Slider("Rescan delay", () -> config.rescanSeconds, value -> config.rescanSeconds = value, 10, 300, "s"))
            .with(new GuiSetting.Toggle("Deep focus", () -> config.deepFocus, value -> config.deepFocus = value))
            .with(new GuiSetting.Info("Flagged chunks", () -> Integer.toString(ArcaneClient.engine().flaggedCount())))
            .with(new GuiSetting.Info("Scan queue", () -> Integer.toString(ArcaneClient.engine().queueSize())))
            .with(new GuiSetting.Bind("Bind", keybinds.scanner()))
            .build();

        GuiModule stashFinder = GuiModule
            .toggle(
                "Stash Finder",
                "Calls out dense storage clusters and labels them in the world as stash candidates.",
                () -> config.stashAlerts,
                value -> config.stashAlerts = value
            )
            .with(new GuiSetting.Info("Candidates", () -> Integer.toString(ArcaneClient.engine().stashCandidates().size())))
            .build();

        GuiModule chunkIntel = GuiModule
            .toggle(
                "Chunk Intel",
                "Breaks down the evidence behind the chunk you are standing in, signal by signal.",
                () -> config.chunkAnalysis,
                value -> config.chunkAnalysis = value
            )
            .build();

        GuiModule growthSignals = signalModule(
            "Growth Signals",
            "Tracks crop stages, farmland alignment, imported plants and other cultivation evidence.",
            () -> config.farmSignals,
            value -> config.farmSignals = value
        );

        GuiModule buildTraces = signalModule(
            "Build Traces",
            "Scores deliberate block placement and interaction patterns left by players.",
            () -> config.playerBlockSignals,
            value -> config.playerBlockSignals = value
        );

        GuiModule machineSignals = signalModule(
            "Machine Signals",
            "Finds automation networks, functional block entities and other working infrastructure.",
            () -> config.machineSignals,
            value -> config.machineSignals = value
        );

        GuiModule liveChanges = signalModule(
            "Live Changes",
            "Uses observed block updates and repeated scans to catch activity while a chunk stays loaded.",
            () -> config.packetSignals,
            value -> config.packetSignals = value
        );

        GuiModule lightSignals = signalModule(
            "Light Signals",
            "Looks for unnatural block light concealed inside otherwise opaque terrain.",
            () -> config.lightSignals,
            value -> config.lightSignals = value
        );

        GuiModule entitySignals = signalModule(
            "Entity Signals",
            "Scores persistent entity clusters while filtering ordinary transient traffic.",
            () -> config.entitySignals,
            value -> config.entitySignals = value
        );

        return List.of(
            chunkFinder,
            growthSignals,
            buildTraces,
            machineSignals,
            liveChanges,
            lightSignals,
            entitySignals,
            stashFinder,
            chunkIntel
        );
    }

    private static GuiModule signalModule(
        String name,
        String description,
        BooleanSupplier value,
        Consumer<Boolean> setter
    ) {
        return GuiModule.toggle(name, description, value, setter).build();
    }

    private static List<GuiModule> esp(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule storage = GuiModule
            .toggle(
                "Storage ESP",
                "Draws chests, barrels, shulkers and other containers through walls.",
                () -> config.esp,
                value -> config.esp = value
            )
            .with(new GuiSetting.Toggle("Tracers", () -> config.storageTracers, value -> config.storageTracers = value))
            .with(new GuiSetting.Info("Visible targets", () -> Integer.toString(EspRenderer.targetCount())))
            .with(new GuiSetting.Bind("Bind", keybinds.esp()))
            .build();

        GuiModule.Builder itemBuilder = GuiModule
            .toggle(
                "Item ESP",
                "Draws dropped loot through walls with a colour per category.",
                () -> config.itemEsp,
                value -> config.itemEsp = value
            )
            .with(new GuiSetting.Toggle("Tracers", () -> config.itemTracers, value -> config.itemTracers = value));
        for (ItemEspCategory category : ItemEspCategory.values()) {
            itemBuilder.with(new GuiSetting.ToggleSwatch(
                category.label(),
                () -> config.itemEspEnabled(category),
                value -> config.setItemEspEnabled(category, value),
                () -> config.itemEspColor(category),
                color -> config.setItemEspColor(category, color)
            ));
        }
        GuiModule item = itemBuilder.with(new GuiSetting.Bind("Bind", keybinds.itemEsp())).build();

        GuiModule tunnel = GuiModule
            .toggle(
                "Tunnel ESP",
                "Traces 1x2 and 3x3 player-dug tunnels back toward whoever dug them.",
                () -> config.tunnelEsp,
                value -> {
                    config.tunnelEsp = value;
                    ArcaneClient.engine().tunnelSettingsChanged(client);
                }
            )
            .with(new GuiSetting.Swatch("1 x 2 tunnels", () -> config.tunnelTwoByOneColor, color -> config.tunnelTwoByOneColor = color))
            .with(new GuiSetting.Swatch("3 x 3 tunnels", () -> config.tunnelThreeByThreeColor, color -> config.tunnelThreeByThreeColor = color))
            .with(new GuiSetting.Bind("Bind", keybinds.tunnelEsp()))
            .build();

        GuiModule tiles = GuiModule
            .toggle(
                "Chunk Tiles",
                "Paints scored chunk tiles over the world so leads stay readable from the air.",
                () -> config.overlay,
                value -> config.overlay = value
            )
            .with(new GuiSetting.Bind("Bind", keybinds.overlay()))
            .build();

        GuiModule debug = GuiModule
            .toggle(
                "ESP Debug",
                "Reports what the ESP pass is drawing right now, for tuning render limits.",
                () -> config.blockEntityDebug,
                value -> config.blockEntityDebug = value
            )
            .with(new GuiSetting.Info("Visible targets", () -> Integer.toString(EspRenderer.targetCount())))
            .build();

        return List.of(storage, item, tunnel, tiles, debug);
    }

    private static List<GuiModule> render(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule radar = GuiModule
            .toggle(
                "Base Radar",
                "Keeps the nearby chunk grid and its live scores in the corner of the screen.",
                () -> config.hud,
                value -> config.hud = value
            )
            .build();

        GuiModule freecam = GuiModule
            .toggle(
                "Freecam",
                "Detaches the camera so you can sweep a suspect chunk without moving your body.",
                FreecamController::isActive,
                value -> {
                    if (value != FreecamController.isActive()) {
                        FreecamController.toggle(client);
                    }
                }
            )
            .with(new GuiSetting.Bind("Bind", keybinds.freecam()))
            .build();

        return List.of(radar, freecam);
    }

    private static List<GuiModule> utility(ArcaneConfig config, ArcaneKeybinds keybinds) {
        GuiModule autoTotem = GuiModule
            .toggle(
                "Auto Totem",
                "Moves a totem of undying back into your off-hand the moment the last one pops.",
                () -> config.autoTotem,
                value -> config.autoTotem = value
            )
            .with(new GuiSetting.Bind("Bind", keybinds.autoTotem()))
            .build();

        GuiModule.Builder macros = GuiModule.toggle(
            "Chat Macros",
            "Sends four saved messages, each on its own key.",
            () -> config.chatMacros,
            value -> config.chatMacros = value
        );
        List<KeyBinding> macroKeys = keybinds.chatMacros();
        for (int slot = 0; slot < macroKeys.size(); slot++) {
            macros.with(new GuiSetting.Message(Integer.toString(slot + 1), slot, macroKeys.get(slot)));
        }

        return List.of(autoTotem, macros.build());
    }

    private static List<GuiModule> clientTools(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule performance = GuiModule
            .value(
                "Performance",
                "Trades scan budget and render targets against frame time.",
                () -> config.performanceProfile().label()
            )
            .with(new GuiSetting.Cycle("Profile", () -> config.performanceProfile().label(), () -> {
                config.cyclePerformanceProfile();
                ArcaneClient.engine().settingsChanged(client);
            }))
            .with(new GuiSetting.Info("Frame rate", () -> client.getCurrentFps() + " FPS"))
            .build();

        GuiModule interfaceModule = GuiModule
            .value(
                "Interface",
                "Theme and shortcut for the Click GUI itself.",
                () -> ClickGuiTheme.fromConfig(config.uiTheme).label()
            )
            .with(new GuiSetting.Cycle(
                "Theme",
                () -> ClickGuiTheme.fromConfig(config.uiTheme).label(),
                () -> config.uiTheme = (config.uiTheme + 1) % ClickGuiTheme.count()
            ))
            .with(new GuiSetting.Bind("Bind", keybinds.settings()))
            .build();

        return List.of(performance, interfaceModule);
    }
}
