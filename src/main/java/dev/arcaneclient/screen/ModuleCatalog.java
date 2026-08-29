package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.ArcaneKeybinds;
import dev.arcaneclient.combat.CombatController;
import dev.arcaneclient.combat.SwingDuration;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.render.EntityEspRenderer;
import dev.arcaneclient.render.EspRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/** Builds and validates Arcane's exact 42-module Click GUI catalog. */
@Environment(EnvType.CLIENT)
public final class ModuleCatalog {
    public static final int EXPECTED_MODULE_COUNT = 42;
    private static final List<String> REQUIRED_MODULE_NAMES = List.of(
        "Auto Totem", "Auto Sprint", "Auto Eat", "Health Alert", "Armor Alert", "Hit Sound", "Swing Speed", "Combat HUD",
        "Storage ESP", "Item ESP", "Tunnel ESP", "Chunk Tiles", "ESP Debug", "Player ESP", "Mob ESP", "Projectile ESP", "Crystal ESP", "Entity Tracers", "Hole ESP",
        "Base Radar", "Freecam", "Freelook", "Fullbright", "No Hurt Cam", "Zoom", "Clean Capture",
        "Performance", "Interface", "Info HUD", "Sound Notifications", "Streamer Mode",
        "Auto Tool", "Chat Macros",
        "Chunk Finder", "Growth Signals", "Build Traces", "Machine Signals", "Live Changes", "Light Signals", "Entity Signals", "Stash Finder", "Chunk Intel"
    );

    private ModuleCatalog() {
    }

    public static List<GuiCategory> build(ArcaneConfig config, MinecraftClient client) {
        ArcaneKeybinds keybinds = ArcaneClient.keybinds();
        List<GuiCategory> categories = List.of(
            new GuiCategory("COMBAT", combat(config, keybinds, client)),
            new GuiCategory("ESP", esp(config, keybinds, client)),
            new GuiCategory("RENDER", render(config, keybinds, client)),
            new GuiCategory("CLIENT", clientTools(config, keybinds, client)),
            new GuiCategory("UTILITY", utility(config, keybinds)),
            new GuiCategory("BASE FINDING", baseFinding(config, keybinds))
        );
        int count = countModules(categories);
        ArrayList<String> actualNames = new ArrayList<>(count);
        for (GuiCategory category : categories) {
            for (GuiModule module : category.modules()) actualNames.add(module.name());
        }
        if (count != EXPECTED_MODULE_COUNT || !actualNames.equals(REQUIRED_MODULE_NAMES)) {
            throw new IllegalStateException("Arcane module catalog does not match the exact 42-module product contract: " + actualNames);
        }
        return categories;
    }

    static List<String> requiredModuleNames() {
        return REQUIRED_MODULE_NAMES;
    }

    static int countModules(List<GuiCategory> categories) {
        int count = 0;
        for (GuiCategory category : categories) count += category.modules().size();
        return count;
    }

    private static List<GuiModule> baseFinding(ArcaneConfig config, ArcaneKeybinds keybinds) {
        GuiModule chunkFinder = GuiModule
            .toggle(
                "Chunk Finder",
                "Scores loaded chunks from the complete 1.6 and 1.8 evidence engine.",
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

        GuiModule stashFinder = GuiModule.toggle(
            "Stash Finder", "Labels high-confidence storage clusters.", () -> config.stashAlerts, value -> config.stashAlerts = value
        ).with(new GuiSetting.Info("Candidates", () -> Integer.toString(ArcaneClient.engine().stashCandidates().size()))).build();

        GuiModule chunkIntel = GuiModule.toggle(
            "Chunk Intel", "Shows the evidence behind the current chunk.", () -> config.chunkAnalysis, value -> config.chunkAnalysis = value
        ).build();

        GuiModule growthSignals = signalModule("Growth Signals", "Crop stages, farmland alignment, imported plants and cultivation evidence.", () -> config.farmSignals, value -> config.farmSignals = value);
        GuiModule buildTraces = signalModule("Build Traces", "Deliberate block placement and interaction patterns.", () -> config.playerBlockSignals, value -> config.playerBlockSignals = value);
        GuiModule machineSignals = signalModule("Machine Signals", "Automation networks and functional block entities.", () -> config.machineSignals, value -> config.machineSignals = value);
        GuiModule liveChanges = signalModule("Live Changes", "Observed block updates and repeated-scan changes.", () -> config.packetSignals, value -> config.packetSignals = value);
        GuiModule lightSignals = signalModule("Light Signals", "Unnatural light concealed in opaque terrain.", () -> config.lightSignals, value -> config.lightSignals = value);
        GuiModule entitySignals = signalModule("Entity Signals", "Persistent entity clusters with transient traffic filtered.", () -> config.entitySignals, value -> config.entitySignals = value);

        return List.of(chunkFinder, growthSignals, buildTraces, machineSignals, liveChanges, lightSignals, entitySignals, stashFinder, chunkIntel);
    }

    private static GuiModule signalModule(String name, String description, BooleanSupplier value, Consumer<Boolean> setter) {
        return GuiModule.toggle(name, description, value, setter).build();
    }

    private static List<GuiModule> esp(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule storage = GuiModule.toggle(
            "Storage ESP", "Draws containers through walls.", () -> config.esp, value -> config.esp = value
        )
            .with(new GuiSetting.Toggle("Tracers", () -> config.storageTracers, value -> config.storageTracers = value))
            .with(new GuiSetting.Info("Visible", () -> Integer.toString(EspRenderer.targetCount())))
            .with(new GuiSetting.Bind("Bind", keybinds.esp()))
            .build();

        GuiModule.Builder itemBuilder = GuiModule.toggle(
            "Item ESP", "Highlights selected dropped-loot categories.", () -> config.itemEsp, value -> config.itemEsp = value
        ).with(new GuiSetting.Toggle("Tracers", () -> config.itemTracers, value -> config.itemTracers = value));
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

        GuiModule tunnel = GuiModule.toggle(
            "Tunnel ESP", "Traces common player-dug tunnel shapes.", () -> config.tunnelEsp, value -> {
                config.tunnelEsp = value;
                ArcaneClient.engine().tunnelSettingsChanged(client);
            }
        )
            .with(new GuiSetting.Swatch("1 x 2", () -> config.tunnelTwoByOneColor, color -> config.tunnelTwoByOneColor = color))
            .with(new GuiSetting.Swatch("3 x 3", () -> config.tunnelThreeByThreeColor, color -> config.tunnelThreeByThreeColor = color))
            .with(new GuiSetting.Bind("Bind", keybinds.tunnelEsp()))
            .build();

        GuiModule tiles = GuiModule.toggle("Chunk Tiles", "Paints scored chunk tiles in the world.", () -> config.overlay, value -> config.overlay = value)
            .with(new GuiSetting.Bind("Bind", keybinds.overlay())).build();
        GuiModule debug = GuiModule.toggle("ESP Debug", "Shows block-entity classifications for tuning.", () -> config.blockEntityDebug, value -> config.blockEntityDebug = value)
            .with(new GuiSetting.Info("Visible", () -> Integer.toString(EspRenderer.targetCount()))).build();

        GuiModule player = GuiModule.toggle("Player ESP", "Outlined players with optional names.", () -> config.playerEsp, value -> config.playerEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.playerEspColor, color -> config.playerEspColor = color))
            .with(new GuiSetting.Toggle("Name tags", () -> config.entityNameTags, value -> config.entityNameTags = value))
            .with(new GuiSetting.Slider("Range", () -> config.entityEspRange, value -> config.entityEspRange = value, 16, 192, "m"))
            .build();
        GuiModule mobs = GuiModule.toggle("Mob ESP", "Outlines living mobs while ignoring armor stands.", () -> config.mobEsp, value -> config.mobEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.mobEspColor, color -> config.mobEspColor = color)).build();
        GuiModule projectiles = GuiModule.toggle("Projectile ESP", "Outlines arrows, pearls, tridents and other projectiles.", () -> config.projectileEsp, value -> config.projectileEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.projectileEspColor, color -> config.projectileEspColor = color)).build();
        GuiModule crystals = GuiModule.toggle("Crystal ESP", "Outlines end crystals separately from mobs.", () -> config.crystalEsp, value -> config.crystalEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.crystalEspColor, color -> config.crystalEspColor = color)).build();
        GuiModule tracers = GuiModule.toggle("Entity Tracers", "Draws view-origin lines to enabled entity ESP targets.", () -> config.entityTracers, value -> config.entityTracers = value)
            .with(new GuiSetting.Info("Targets", () -> Integer.toString(EntityEspRenderer.targetCount()))).build();
        GuiModule holes = GuiModule.toggle("Hole ESP", "Marks one-block obsidian or bedrock safety holes.", () -> config.holeEsp, value -> config.holeEsp = value)
            .with(new GuiSetting.Slider("Range", () -> config.holeEspRange, value -> config.holeEspRange = value, 4, 16, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.holeEspColor, color -> config.holeEspColor = color)).build();

        return List.of(storage, item, tunnel, tiles, debug, player, mobs, projectiles, crystals, tracers, holes);
    }

    private static List<GuiModule> combat(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule autoTotem = GuiModule.toggle("Auto Totem", "Refills the off-hand from inventory after a pop.", () -> config.autoTotem, value -> config.autoTotem = value)
            .with(new GuiSetting.Info("Totems", () -> client.player == null ? "0" : Integer.toString(CombatController.totemCount(client.player))))
            .with(new GuiSetting.Bind("Bind", keybinds.autoTotem())).build();
        GuiModule autoSprint = GuiModule.toggle("Auto Sprint", "Sprints while moving forward when hunger allows.", () -> config.autoSprint, value -> config.autoSprint = value).build();
        GuiModule autoEat = GuiModule.toggle("Auto Eat", "Selects and eats hotbar food at the chosen hunger level.", () -> config.autoEat, value -> config.autoEat = value)
            .with(new GuiSetting.Slider("Hunger", () -> config.autoEatHunger, value -> config.autoEatHunger = value, 1, 20, "")).build();
        GuiModule health = GuiModule.toggle("Health Alert", "Actionbar and sound warning when health crosses the threshold.", () -> config.lowHealthAlert, value -> config.lowHealthAlert = value)
            .with(new GuiSetting.Slider("Hearts", () -> config.lowHealthHearts, value -> config.lowHealthHearts = value, 1, 10, "♥")).build();
        GuiModule armor = GuiModule.toggle("Armor Alert", "Warns once when the weakest equipped armor reaches the threshold.", () -> config.armorAlert, value -> config.armorAlert = value)
            .with(new GuiSetting.Slider("Durability", () -> config.armorAlertPercent, value -> config.armorAlertPercent = value, 1, 100, "%")).build();
        GuiModule hitSound = GuiModule.toggle("Hit Sound", "Plays a clean confirmation tone on entity attacks.", () -> config.hitSound, value -> config.hitSound = value).build();
        GuiModule swing = GuiModule.toggle("Swing Speed", "Lengthens the local hand animation; more ticks means a slower swing.", () -> config.swingSpeed, value -> config.swingSpeed = value)
            .with(new GuiSetting.Slider("Slow duration", () -> config.swingDuration, value -> config.swingDuration = value, SwingDuration.MIN_TICKS, SwingDuration.MAX_TICKS, "t")).build();
        GuiModule combatHud = GuiModule.toggle("Combat HUD", "Shows attack cooldown and optional totem count near the crosshair.", () -> config.attackMeter, value -> config.attackMeter = value)
            .with(new GuiSetting.Toggle("Totem count", () -> config.totemCounter, value -> config.totemCounter = value)).build();
        return List.of(autoTotem, autoSprint, autoEat, health, armor, hitSound, swing, combatHud);
    }

    private static List<GuiModule> render(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule radar = GuiModule.toggle("Base Radar", "Nearby scored-chunk grid.", () -> config.hud, value -> config.hud = value).build();
        GuiModule freecam = GuiModule.toggle("Freecam", "Smooth detached flight; wheel changes speed and mining uses body reach.", FreecamController::isActive, value -> {
            if (value != FreecamController.isActive()) FreecamController.toggle(client);
        })
            .with(new GuiSetting.Slider("Speed", () -> config.freecamSpeed, value -> config.freecamSpeed = value, 1, 20, ""))
            .with(new GuiSetting.Toggle("Directional mining", () -> config.freecamMining, value -> config.freecamMining = value))
            .with(new GuiSetting.Bind("Bind", keybinds.freecam())).build();
        GuiModule freelook = GuiModule.toggle("Freelook", "Orbit around your real skin while movement and mining keep using your actual aim.", FreelookController::isActive, value -> {
            if (value != FreelookController.isActive()) FreelookController.toggle(client);
        }).with(new GuiSetting.Bind("Bind", keybinds.freelook())).build();
        GuiModule fullbright = GuiModule.toggle("Fullbright", "Applies full client-side night-vision brightness.", () -> config.fullbright, value -> config.fullbright = value).build();
        GuiModule hurtCam = GuiModule.toggle("No Hurt Cam", "Removes the damage tilt without changing damage feedback.", () -> config.noHurtCam, value -> config.noHurtCam = value).build();
        GuiModule zoom = GuiModule.toggle("Zoom", "Hold the zoom key for a configurable smooth FOV reduction.", () -> config.zoom, value -> config.zoom = value)
            .with(new GuiSetting.Slider("FOV scale", () -> config.zoomPercent, value -> config.zoomPercent = value, 10, 90, "%"))
            .with(new GuiSetting.Bind("Hold bind", keybinds.zoom())).build();
        GuiModule clean = GuiModule.toggle("Clean Capture", "Hides Arcane overlays for screenshots and recordings.", () -> config.cleanCapture, value -> config.cleanCapture = value)
            .with(new GuiSetting.Bind("Bind", keybinds.cleanCapture())).build();
        return List.of(radar, freecam, freelook, fullbright, hurtCam, zoom, clean);
    }

    private static List<GuiModule> utility(ArcaneConfig config, ArcaneKeybinds keybinds) {
        GuiModule autoTool = GuiModule.toggle("Auto Tool", "Selects the best hotbar tool before mining, then restores your slot.", () -> config.autoTool, value -> config.autoTool = value)
            .with(new GuiSetting.Toggle("Protect 1 durability", () -> config.autoToolPreserveDurability, value -> config.autoToolPreserveDurability = value)).build();
        GuiModule.Builder macros = GuiModule.toggle("Chat Macros", "Sends four saved messages, each on its own key.", () -> config.chatMacros, value -> config.chatMacros = value);
        List<KeyBinding> macroKeys = keybinds.chatMacros();
        for (int slot = 0; slot < macroKeys.size(); slot++) macros.with(new GuiSetting.Message(Integer.toString(slot + 1), slot, macroKeys.get(slot)));
        return List.of(autoTool, macros.build());
    }

    private static List<GuiModule> clientTools(ArcaneConfig config, ArcaneKeybinds keybinds, MinecraftClient client) {
        GuiModule performance = GuiModule.value("Performance", "Balances scan and render work against frame time.", () -> config.performanceProfile().label())
            .with(new GuiSetting.Cycle("Profile", () -> config.performanceProfile().label(), () -> {
                config.cyclePerformanceProfile();
                ArcaneClient.engine().settingsChanged(client);
            }))
            .with(new GuiSetting.Info("Frame rate", () -> client.getCurrentFps() + " FPS")).build();

        GuiModule interfaceModule = GuiModule.value("Interface", "Preset themes or exact custom RGB colors.", () -> config.customUiColors ? "CUSTOM" : ClickGuiTheme.fromConfig(config.uiTheme).label())
            .with(new GuiSetting.Cycle("Theme", () -> ClickGuiTheme.fromConfig(config.uiTheme).label(), () -> config.uiTheme = (config.uiTheme + 1) % ClickGuiTheme.count()))
            .with(new GuiSetting.Toggle("Custom RGB", () -> config.customUiColors, value -> config.customUiColors = value))
            .with(channel("Accent red", 16, () -> config.uiAccentColor, value -> config.uiAccentColor = value))
            .with(channel("Accent green", 8, () -> config.uiAccentColor, value -> config.uiAccentColor = value))
            .with(channel("Accent blue", 0, () -> config.uiAccentColor, value -> config.uiAccentColor = value))
            .with(channel("Panel red", 16, () -> config.uiPanelColor, value -> config.uiPanelColor = value))
            .with(channel("Panel green", 8, () -> config.uiPanelColor, value -> config.uiPanelColor = value))
            .with(channel("Panel blue", 0, () -> config.uiPanelColor, value -> config.uiPanelColor = value))
            .with(channel("Text red", 16, () -> config.uiTextColor, value -> config.uiTextColor = value))
            .with(channel("Text green", 8, () -> config.uiTextColor, value -> config.uiTextColor = value))
            .with(channel("Text blue", 0, () -> config.uiTextColor, value -> config.uiTextColor = value))
            .with(new GuiSetting.Bind("Bind", keybinds.settings())).build();

        GuiModule infoHud = GuiModule.toggle("Info HUD", "Compact live FPS, position, direction, movement, ping, and biome readout.", () -> config.infoHud, value -> config.infoHud = value)
            .with(new GuiSetting.Toggle("FPS", () -> config.infoFps, value -> config.infoFps = value))
            .with(new GuiSetting.Toggle("Coordinates", () -> config.infoCoordinates, value -> config.infoCoordinates = value))
            .with(new GuiSetting.Toggle("Direction", () -> config.infoDirection, value -> config.infoDirection = value))
            .with(new GuiSetting.Toggle("Movement speed", () -> config.infoSpeed, value -> config.infoSpeed = value))
            .with(new GuiSetting.Toggle("Ping", () -> config.infoPing, value -> config.infoPing = value))
            .with(new GuiSetting.Toggle("Biome", () -> config.infoBiome, value -> config.infoBiome = value)).build();

        GuiModule sounds = GuiModule.toggle("Sound Notifications", "Master volume for combat and safety notification tones.", () -> config.soundNotifications, value -> config.soundNotifications = value)
            .with(new GuiSetting.Slider("Volume", () -> config.notificationVolume, value -> config.notificationVolume = value, 0, 100, "%")).build();
        GuiModule streamer = GuiModule.toggle("Streamer Mode", "Redacts coordinates and player names from Arcane overlays.", () -> config.streamerMode, value -> config.streamerMode = value).build();
        return List.of(performance, interfaceModule, infoHud, sounds, streamer);
    }

    private static GuiSetting.Slider channel(
        String label,
        int shift,
        java.util.function.IntSupplier color,
        java.util.function.IntConsumer setter
    ) {
        return new GuiSetting.Slider(label, () -> ArcaneConfig.channel(color.getAsInt(), shift), value -> setter.accept(ArcaneConfig.withChannel(color.getAsInt(), shift, value)), 0, 255, "");
    }
}
