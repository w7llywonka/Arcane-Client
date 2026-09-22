package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.ArcaneKeybinds;
import dev.arcaneclient.combat.CombatController;
import dev.arcaneclient.combat.SwingDuration;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreecamSpeed;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.model.WorldObservation;
import dev.arcaneclient.render.EntityEspRenderer;
import dev.arcaneclient.render.EspRenderer;
import dev.arcaneclient.render.WorldIntelRenderer;
import dev.arcaneclient.utility.ElytraAssistController;
import dev.arcaneclient.utility.RelogController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Builds and validates Arcane's visible catalog of independently useful modules. */
@Environment(EnvType.CLIENT)
public final class ModuleCatalog {
    /** Functional anchors guard accidental deletion without turning a marketing count into correctness. */
    private static final List<String> REQUIRED_CORE_MODULE_NAMES = List.of(
        "Chunk Finder", "Chunk Intel", "Evidence Points", "Auto Totem", "Auto Armor", "Inventory Move",
        "Storage ESP", "ESP Debug", "Amethyst ESP", "Access Trail ESP", "Search", "Freecam", "No Render", "Auto Tool",
        "Durability Guard", "Performance", "Info HUD"
    );

    private ModuleCatalog() {
    }

    public static List<GuiCategory> build(ArcaneConfig config, Minecraft client) {
        ArcaneKeybinds keybinds = ArcaneClient.keybinds();
        List<GuiModule> combatModules = mutable(combined(combat(config, keybinds, client),
            dev.arcaneclient.additions.combat.CombatAdditionsController.modules(config, client)));
        List<GuiModule> movementModules = mutable(movement(config, keybinds, client));
        List<GuiModule> espModules = mutable(combined(esp(config, keybinds, client),
            dev.arcaneclient.additions.intel.IntelAdditions.espModules(config, client)));
        List<GuiModule> renderModules = mutable(combined(render(config, keybinds, client),
            dev.arcaneclient.additions.visual.VisualAdditions.modules(config, client),
            dev.arcaneclient.additions.intel.IntelAdditions.renderModules(config, client),
            dev.arcaneclient.additions.viewmodel.ViewmodelEditor.modules(config, client),
            dev.arcaneclient.additions.mining.MiningOverlay.modules(config, client),
            dev.arcaneclient.additions.effects.Effects.modules(config, client)));
        List<GuiModule> utilityModules = mutable(combined(utility(config, keybinds),
            dev.arcaneclient.additions.nuker.Nuker.modules(config, client),
            dev.arcaneclient.additions.utility.UtilityAdditions.modules(config, client),
            dev.arcaneclient.additions.intel.IntelAdditions.utilityModules(config, client),
            dev.arcaneclient.additions.social.AutoTpa.modules(config, client),
            dev.arcaneclient.additions.dispenser.DispenserHelper.modules(config, client)));
        List<GuiModule> clientModules = mutable(combined(clientTools(config, keybinds, client),
            dev.arcaneclient.additions.media.MediaHud.modules(config, client),
            dev.arcaneclient.additions.preview.PreviewAdditions.modules(config, client),
            dev.arcaneclient.additions.configlibrary.ConfigLibrary.modules(config, client),
            dev.arcaneclient.additions.presence.ArcanePresence.modules(config, client)));
        List<GuiModule> baseModules = mutable(combined(baseFinding(config, keybinds),
            dev.arcaneclient.additions.susfinder.SusChunkFinderModules.modules(config, client)));

        // Put discovery, privacy, session and flight tools where users naturally look for them.
        moveNamed(espModules, baseModules, "Chunk Tiles", "Tunnel ESP", "Amethyst ESP", "Access Trail ESP");
        moveNamed(renderModules, baseModules, "Base Radar", "Region Map");
        moveNamed(renderModules, clientModules, "Clean Capture", "Skin Protect");
        moveNamed(utilityModules, movementModules, "Elytra Swap");
        moveNamed(clientModules, utilityModules, "Relog");
        moveNamed(renderModules, espModules, "Hitboxes");
        removeNamed(clientModules, "Config Library"); // The persistent Configs button already opens it.
        config.uiFavorites.remove("Config Library");
        if (config.uiFavorites.remove("Coordinate Clipboard")) config.uiFavorites.add("Copy Coordinates");

        groupNamed(config, combatModules, "Targeting", "Aim and attack helpers.",
            "Aim Assist", "Auto Clicker", "Trigger Bot");
        groupNamed(config, combatModules, "Weapon Assist", "Automatic weapon choices for specific combat situations.",
            "Smart Weapon", "Spear Switch", "Mace Switch", "Shield Breaker");
        groupNamed(config, combatModules, "Combat Feedback", "Local combat sound, swing and cooldown presentation.",
            "Hit Sound", "Swing Speed", "Combat HUD");
        groupNamed(config, combatModules, "Anchor Assist", "Safe, deliberate respawn-anchor actions.",
            "Anchor Macro", "Double Anchor");

        groupNamed(config, movementModules, "Auto Movement", "Simple movement holds and automatic movement inputs.",
            "Auto Sprint", "Auto Walk", "Auto Jump", "Auto Sneak");
        groupNamed(config, movementModules, "Elytra", "Flight boosting, camera and equipment controls.",
            "Elytra Assist", "Elytra Perspective");

        groupNamed(config, espModules, "Entity ESP", "Entity outlines, tracers and hitbox rendering in one place.",
            "Player ESP", "Mob ESP", "Projectile ESP", "Crystal ESP", "Entity Tracers", "Hitboxes");
        groupNamed(config, espModules, "Nametags", "Player, dropped-item and spawner labels.",
            "Nametags", "Spawner Nametags");

        groupNamed(config, renderModules, "Camera Tools", "Camera, zoom, FOV and hand-view controls.",
            "Freelook", "Zoom", "Custom FOV", "Viewmodel Editor");
        groupNamed(config, renderModules, "Navigation", "Waypoints, breadcrumbs and chunk boundaries.",
            "Waypoints", "Breadcrumbs", "Chunk Borders");
        groupNamed(config, renderModules, "World Effects", "Local motion, impact and particle presentation.",
            "Jump Circles", "Hit Particles", "Motion Blur", "Totem Animation", "Particle Control");
        groupNamed(config, renderModules, "Styling", "Crosshair, outlines, glint and local accessories.",
            "Custom Crosshair", "Block Outline", "Custom Glint", "Custom Accessories", "Armor Trim Hider");
        groupNamed(config, renderModules, "HUD Indicators", "Compact input and active-feature indicators.",
            "Keystrokes", "Active Modules");

        groupNamed(config, utilityModules, "Session", "Respawn, reconnect, idle and death-session helpers.",
            "Auto Respawn", "Death Tracker", "Anti AFK");
        groupNamed(config, utilityModules, "Totem Inventory", "Small, deliberate totem inventory automations.",
            "Hover Totem", "Auto Inventory Totem");
        groupNamed(config, utilityModules, "Social", "Chat shortcuts and trusted teleport requests.",
            "Chat Macros", "Auto TPA");
        groupNamed(config, utilityModules, "Alerts", "Spawner, staff-list and weather notifications.",
            "Spawner Protect", "Staff List", "Weather Notifier");

        groupNamed(config, clientModules, "HUD Widgets", "Optional status, inventory and media panels.",
            "Status HUD", "Inventory HUD", "Spotify HUD");
        groupNamed(config, clientModules, "Preview Lab", "Clearly labeled local-only presentation previews.",
            "Fake Pay", "Fake Roles", "Fake Stats");
        groupNamed(config, clientModules, "Privacy & Capture", "Redaction, clean recording and local skin privacy.",
            "Streamer Mode", "Clean Capture", "Skin Protect");

        List<GuiCategory> categories = List.of(
            new GuiCategory("COMBAT", List.copyOf(combatModules)),
            new GuiCategory("MOVEMENT", List.copyOf(movementModules)),
            new GuiCategory("ESP", List.copyOf(espModules)),
            new GuiCategory("RENDER", List.copyOf(renderModules)),
            new GuiCategory("UTILITY", List.copyOf(utilityModules)),
            new GuiCategory("CLIENT", List.copyOf(clientModules)),
            new GuiCategory("BASE FINDING", List.copyOf(baseModules))
        );
        ArrayList<String> actualNames = new ArrayList<>(countModules(categories));
        for (GuiCategory category : categories) {
            for (GuiModule module : category.modules()) actualNames.add(module.name());
        }
        if (new HashSet<>(actualNames).size() != actualNames.size() || !actualNames.containsAll(REQUIRED_CORE_MODULE_NAMES)) {
            throw new IllegalStateException("Arcane module catalog has a duplicate or missing functional core: " + actualNames);
        }
        return categories;
    }

    static List<String> requiredCoreModuleNames() {
        return REQUIRED_CORE_MODULE_NAMES;
    }

    @SafeVarargs
    private static List<GuiModule> combined(List<GuiModule>... groups) {
        ArrayList<GuiModule> result = new ArrayList<>();
        for (List<GuiModule> group : groups) result.addAll(group);
        return List.copyOf(result);
    }

    private static List<GuiModule> mutable(List<GuiModule> modules) {
        return new ArrayList<>(modules);
    }

    private static void moveNamed(List<GuiModule> source, List<GuiModule> destination, String... names) {
        for (String name : names) {
            GuiModule module = findNamed(source, name);
            source.remove(module);
            destination.add(module);
        }
    }

    private static void removeNamed(List<GuiModule> modules, String name) {
        modules.remove(findNamed(modules, name));
    }

    private static GuiModule findNamed(List<GuiModule> modules, String name) {
        return modules.stream().filter(module -> module.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing module during catalog cleanup: " + name));
    }

    private static void groupNamed(
        ArcaneConfig config,
        List<GuiModule> modules,
        String groupName,
        String description,
        String... childNames
    ) {
        List<GuiModule> children = new ArrayList<>(childNames.length);
        int insertion = modules.size();
        boolean favorite = false;
        for (String childName : childNames) {
            GuiModule child = findNamed(modules, childName);
            children.add(child);
            insertion = Math.min(insertion, modules.indexOf(child));
            favorite |= config.uiFavorites.remove(childName);
        }
        List<GuiModule> groupedChildren = List.copyOf(children);
        GuiModule.Builder group = GuiModule.group(groupName, description,
            () -> groupedChildren.stream().anyMatch(GuiModule::enabled),
            () -> {
                long count = groupedChildren.stream().filter(GuiModule::enabled).count();
                return count == 0 ? "" : count + " ON";
            });
        for (GuiModule child : groupedChildren) {
            if (child.toggleable()) {
                group.with(new GuiSetting.Toggle(child.name(), child.description(), child::enabled, enabled -> {
                    if (enabled != child.enabled()) child.toggle();
                }));
            } else {
                group.with(new GuiSetting.Section(child.name()));
            }
            for (GuiSetting setting : child.settings()) group.with(setting);
        }
        modules.removeAll(groupedChildren);
        modules.add(insertion, group.build());
        if (favorite) config.uiFavorites.add(groupName);
    }

    static int countModules(List<GuiCategory> categories) {
        int count = 0;
        for (GuiCategory category : categories) count += category.modules().size();
        return count;
    }

    private static List<GuiModule> baseFinding(ArcaneConfig config, ArcaneKeybinds keybinds) {
        GuiModule chunkFinder = GuiModule.toggle(
            "Chunk Finder",
            "Counts grown plant blocks in loaded chunks. No containers, saplings, sounds or waiting for growth ticks.",
            () -> config.enabled,
            value -> config.enabled = value
        )
            .with(new GuiSetting.Slider("Grown blocks required", () -> config.grownBlocksRequired, value -> config.grownBlocksRequired = value, 1, 256, " blocks"))
            .with(new GuiSetting.Slider("Scan radius", () -> config.scanRadius, value -> config.scanRadius = value, 2, 24, " ch"))
            .with(new GuiSetting.Slider("Scan speed", () -> config.chunksPerTick, value -> config.chunksPerTick = value, 1, 16, "/t"))
            .with(new GuiSetting.Slider("Rescan delay", () -> config.rescanSeconds, value -> config.rescanSeconds = value, 10, 300, "s"))
            .with(new GuiSetting.Toggle("Neighbor correlation", () -> config.clusterInference, value -> config.clusterInference = value))
            .with(new GuiSetting.Info("Flagged chunks", () -> Integer.toString(ArcaneClient.engine().flaggedCount())))
            .with(new GuiSetting.Info("Base leads", () -> Integer.toString(ArcaneClient.engine().activityClusterCount())))
            .with(new GuiSetting.Info("Scan queue", () -> Integer.toString(ArcaneClient.engine().queueSize())))
            .with(new GuiSetting.Bind("Bind", keybinds.scanner()))
            .build();
        GuiModule chunkIntel = GuiModule.toggle(
            "Chunk Intel",
            "Shows the evidence families and score that produced the current chunk result.",
            () -> config.chunkAnalysis,
            value -> config.chunkAnalysis = value
        ).build();
        GuiModule evidencePoints = GuiModule.toggle(
            "Evidence Points",
            "Renders a bounded set of exact scanner observations, colored by evidence family.",
            () -> config.evidencePoints,
            value -> config.evidencePoints = value
        ).build();

        return List.of(chunkFinder, chunkIntel, evidencePoints);
    }

    private static List<GuiModule> esp(ArcaneConfig config, ArcaneKeybinds keybinds, Minecraft client) {
        GuiModule storage = GuiModule.toggle(
            "Storage ESP", "Draws loaded containers through walls with no vertical cutoff.", () -> config.esp, value -> config.esp = value
        )
            .with(new GuiSetting.Toggle("Tracers", () -> config.storageTracers, value -> config.storageTracers = value))
            .with(new GuiSetting.Toggle("Chat alerts", () -> config.storageChatAlerts, value -> config.storageChatAlerts = value))
            .with(new GuiSetting.Info("Vertical range", () -> "World bottom"))
            .with(new GuiSetting.Info("Visible", () -> Integer.toString(EspRenderer.targetCount())))
            .with(new GuiSetting.Bind("Bind", keybinds.esp()))
            .build();

        GuiModule debug = GuiModule.toggle(
            "ESP Debug",
            "Draws loaded block entities below Y 0 only; surface block entities are ignored.",
            () -> config.blockEntityDebug,
            value -> config.blockEntityDebug = value
        )
            .with(new GuiSetting.Toggle("Tracers", () -> config.blockEntityDebugTracers, value -> config.blockEntityDebugTracers = value))
            .with(new GuiSetting.Info("Depth filter", () -> "Y < 0"))
            .with(new GuiSetting.Info("Visible", () -> Integer.toString(EspRenderer.debugTargetCount())))
            .build();

        GuiModule.Builder itemBuilder = GuiModule.toggle(
            "Item ESP", "Highlights any selected dropped item, or every dropped item. Only items received from the server can be shown.", () -> config.itemEsp, value -> config.itemEsp = value
        )
            .with(new GuiSetting.Cycle("Choose items", () -> Integer.toString(config.itemEspItems.size()), () -> client.gui.setScreen(new ItemEspPickerScreen(client.gui.screen(), config))))
            .with(new GuiSetting.Cycle("Add held item", () -> "Add", () -> {
                if (client.player != null && !client.player.getMainHandItem().isEmpty()) {
                    config.itemEspItems.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(client.player.getMainHandItem().getItem()).toString());
                }
            }))
            .with(new GuiSetting.Toggle("All dropped items", () -> config.itemEspAll, value -> config.itemEspAll = value))
            .with(new GuiSetting.Toggle("Preset groups", () -> config.itemEspUsePresets, value -> config.itemEspUsePresets = value))
            .with(new GuiSetting.Swatch("Item color", () -> config.itemEspCustomColor, value -> config.itemEspCustomColor = value))
            .with(new GuiSetting.Slider("Range", () -> config.itemEspRange, value -> config.itemEspRange = value, 16, 256, "m"))
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

        GuiModule amethyst = GuiModule.toggle(
            "Amethyst ESP",
            "Marks exact loaded or server-revealed buds and clusters; marker size shows the real four-stage growth state.",
            () -> config.amethystEsp,
            value -> {
                config.amethystEsp = value;
                ArcaneClient.engine().settingsChanged(client);
            }
        )
            .with(new GuiSetting.Slider("Range", () -> config.amethystEspRange, value -> config.amethystEspRange = value, 16, 384, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.amethystEspColor, color -> config.amethystEspColor = color))
            .with(new GuiSetting.Info("Observed shards", () -> Integer.toString(
                ArcaneClient.engine().worldObservationCount(WorldObservation.Kind.AMETHYST_SHARD)
            )))
            .with(new GuiSetting.Info("Stages S/M/L/C", () ->
                ArcaneClient.engine().amethystStageCount(0) + "/"
                    + ArcaneClient.engine().amethystStageCount(1) + "/"
                    + ArcaneClient.engine().amethystStageCount(2) + "/"
                    + ArcaneClient.engine().amethystStageCount(3)
            ))
            .build();

        GuiModule accessTrails = GuiModule.toggle(
            "Access Trail ESP",
            "Highlights geometry-confirmed cobbled-deepslate descents as a display-only overlay; they never affect base scoring.",
            () -> config.accessTrailEsp,
            value -> {
                config.accessTrailEsp = value;
                ArcaneClient.engine().settingsChanged(client);
            }
        )
            .with(new GuiSetting.Slider("Range", () -> config.accessTrailEspRange, value -> config.accessTrailEspRange = value, 16, 384, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.accessTrailEspColor, color -> config.accessTrailEspColor = color))
            .with(new GuiSetting.Info("Trail blocks", () -> Integer.toString(
                ArcaneClient.engine().worldObservationCount(WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL)
            )))
            .build();

        GuiModule tiles = GuiModule.toggle("Chunk Tiles", "Shows completed chunk scans and highlights scored Chunk Finder results.", () -> config.overlay, value -> config.overlay = value)
            .with(new GuiSetting.Bind("Bind", keybinds.overlay())).build();

        GuiModule player = GuiModule.toggle("Player ESP", "Outlines loaded players independently from their nametags.", () -> config.playerEsp, value -> config.playerEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.playerEspColor, color -> config.playerEspColor = color))
            .with(new GuiSetting.Slider("Shared entity range", () -> config.entityEspRange, value -> config.entityEspRange = value, 16, 192, "m"))
            .build();
        GuiModule mobs = GuiModule.toggle("Mob ESP", "Outlines living mobs while ignoring armor stands.", () -> config.mobEsp, value -> config.mobEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.mobEspColor, color -> config.mobEspColor = color)).build();
        GuiModule projectiles = GuiModule.toggle("Projectile ESP", "Outlines arrows, pearls, tridents and other projectiles.", () -> config.projectileEsp, value -> config.projectileEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.projectileEspColor, color -> config.projectileEspColor = color)).build();
        GuiModule crystals = GuiModule.toggle("Crystal ESP", "Outlines end crystals separately from mobs.", () -> config.crystalEsp, value -> config.crystalEsp = value)
            .with(new GuiSetting.Swatch("Color", () -> config.crystalEspColor, color -> config.crystalEspColor = color)).build();
        GuiModule tracers = GuiModule.toggle("Entity Tracers", "Draws to loaded targets in horizontal range, including deep underground.", () -> config.entityTracers, value -> config.entityTracers = value)
            .with(new GuiSetting.Slider("Horizontal range", () -> config.entityEspRange, value -> config.entityEspRange = value, 16, 192, "m"))
            .with(new GuiSetting.Info("Vertical range", () -> "World bottom"))
            .with(new GuiSetting.Info("Tracer targets", () -> Integer.toString(EntityEspRenderer.tracerTargetCount()))).build();
        GuiModule holes = GuiModule.toggle("Hole ESP", "Marks one-block obsidian or bedrock safety holes.", () -> config.holeEsp, value -> config.holeEsp = value)
            .with(new GuiSetting.Slider("Range", () -> config.holeEspRange, value -> config.holeEspRange = value, 4, 16, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.holeEspColor, color -> config.holeEspColor = color)).build();
        GuiModule search = GuiModule.toggle("Search", "Indexes spawners, trial spawners, vaults, beacons, lodestones, respawn anchors, and ancient debris.", () -> config.searchEsp, value -> config.searchEsp = value)
            .with(new GuiSetting.Slider("Range", () -> config.searchEspRange, value -> config.searchEspRange = value, 16, 192, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.searchEspColor, color -> config.searchEspColor = color))
            .with(new GuiSetting.Info("Indexed targets", () -> Integer.toString(WorldIntelRenderer.searchTargetCount())))
            .with(new GuiSetting.Info("Queued chunks", () -> Integer.toString(WorldIntelRenderer.queuedChunkCount()))).build();
        GuiModule.Builder nametagBuilder = GuiModule.toggle("Nametags", "Player labels, equipment and dropped-item names. Choose only the details you need; Streamer Mode redacts names.", () -> config.nametags, value -> config.nametags = value)
            .with(new GuiSetting.Slider("Range", () -> config.nametagRange, value -> config.nametagRange = value, 16, 192, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.nametagColor, color -> config.nametagColor = color));
        for (GuiSetting setting : dev.arcaneclient.additions.cosmetics.NametagSettings.settings(config)) nametagBuilder.with(setting);
        GuiModule nametags = nametagBuilder.build();
        GuiModule logoutSpots = GuiModule.toggle("Logout Spots", "Keeps temporary markers where tracked players disappear from a loaded world.", () -> config.logoutSpots, value -> config.logoutSpots = value)
            .with(new GuiSetting.Slider("Lifetime", () -> config.logoutSpotMinutes, value -> config.logoutSpotMinutes = value, 1, 120, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.logoutSpotColor, color -> config.logoutSpotColor = color)).build();
        GuiModule portalEsp = GuiModule.toggle("Portal ESP", "Highlights loaded Nether and End portal blocks without treating them as base evidence.", () -> config.portalEsp, value -> config.portalEsp = value)
            .with(new GuiSetting.Slider("Range", () -> config.portalEspRange, value -> config.portalEspRange = value, 16, 192, "m"))
            .with(new GuiSetting.Swatch("Color", () -> config.portalEspColor, color -> config.portalEspColor = color)).build();

        return List.of(storage, debug, item, tunnel, amethyst, accessTrails, tiles, player, mobs, projectiles, crystals, tracers, holes, search, nametags, logoutSpots, portalEsp);
    }

    private static List<GuiModule> combat(ArcaneConfig config, ArcaneKeybinds keybinds, Minecraft client) {
        GuiModule autoTotem = GuiModule.toggle("Auto Totem", "Refills the off-hand from inventory after a totem pop.", () -> config.autoTotem, value -> config.autoTotem = value)
            .with(new GuiSetting.Info("Totems", () -> client.player == null ? "0" : Integer.toString(CombatController.totemCount(client.player))))
            .with(new GuiSetting.Bind("Bind", keybinds.autoTotem())).build();
        GuiModule autoEat = GuiModule.toggle("Auto Eat", "Selects safe hotbar food at or below the chosen hunger level without consuming golden apples.", () -> config.autoEat, value -> config.autoEat = value)
            .with(new GuiSetting.Slider("Hunger", () -> config.autoEatHunger, value -> config.autoEatHunger = value, 1, 19, "")).build();
        GuiModule autoArmor = GuiModule.toggle("Auto Armor", "Equips real armor upgrades from the player inventory with a deliberate click delay.", () -> config.autoArmor, value -> config.autoArmor = value)
            .with(new GuiSetting.Slider("Click delay", () -> config.autoArmorDelayTicks, value -> config.autoArmorDelayTicks = value, 1, 20, "t")).build();
        GuiModule smartWeapon = GuiModule.toggle("Smart Weapon", "Selects the strongest hotbar weapon for the targeted living entity, then restores your slot.", () -> config.smartWeapon, value -> config.smartWeapon = value).build();
        GuiModule triggerBot = GuiModule.toggle("Trigger Bot", "Attacks a valid crosshair target only when the vanilla attack cooldown is ready.", () -> config.triggerBot, value -> config.triggerBot = value)
            .with(new GuiSetting.Slider("Extra delay", () -> config.triggerBotDelayTicks, value -> config.triggerBotDelayTicks = value, 0, 10, "t")).build();
        GuiModule refill = GuiModule.toggle("Hotbar Refill", "Moves matching inventory stacks into depleted unselected hotbar stacks.", () -> config.hotbarRefill, value -> config.hotbarRefill = value)
            .with(new GuiSetting.Slider("Refill below", () -> config.hotbarRefillThreshold, value -> config.hotbarRefillThreshold = value, 1, 63, "")).build();
        GuiModule spear = GuiModule.toggle("Spear Switch", "Prefers a spear-style weapon at useful mid-range rather than replacing close-range choices.", () -> config.spearSwitch, value -> config.spearSwitch = value).build();
        GuiModule mace = GuiModule.toggle("Mace Switch", "Selects a mace only when fall distance makes the smash attack worthwhile.", () -> config.maceSwitch, value -> config.maceSwitch = value).build();
        GuiModule safety = GuiModule.toggle("Safety Alerts", "Combines survival, flight, and proximity rules into one latched warning policy; it never disconnects automatically.", () -> config.safetyDisconnect, value -> config.safetyDisconnect = value)
            .with(new GuiSetting.Info("Action", () -> "WARN ONLY"))
            .with(new GuiSetting.Toggle("Low health", () -> config.safetyRuleHealth, value -> config.safetyRuleHealth = value))
            .with(new GuiSetting.Slider("Health threshold", () -> config.lowHealthHearts, value -> config.lowHealthHearts = value, 1, 10, " hearts"))
            .with(new GuiSetting.Toggle("Low totems", () -> config.safetyRuleTotems, value -> config.safetyRuleTotems = value))
            .with(new GuiSetting.Slider("Totem reserve", () -> config.safetyTotemMinimum, value -> config.safetyTotemMinimum = value, 0, 8, ""))
            .with(new GuiSetting.Toggle("Low armor", () -> config.safetyRuleArmor, value -> config.safetyRuleArmor = value))
            .with(new GuiSetting.Slider("Armor threshold", () -> config.armorAlertPercent, value -> config.armorAlertPercent = value, 1, 50, "%"))
            .with(new GuiSetting.Toggle("Flight reserves", () -> config.safetyRuleFlight, value -> config.safetyRuleFlight = value))
            .with(new GuiSetting.Slider("Elytra threshold", () -> config.flightSafetyDurability, value -> config.flightSafetyDurability = value, 1, 50, "%"))
            .with(new GuiSetting.Slider("Rocket reserve", () -> config.flightSafetyRockets, value -> config.flightSafetyRockets = value, 1, 32, ""))
            .with(new GuiSetting.Toggle("Player proximity", () -> config.safetyRuleProximity, value -> config.safetyRuleProximity = value))
            .with(new GuiSetting.Slider("Proximity range", () -> config.nearbyPlayerRange, value -> config.nearbyPlayerRange = value, 8, 160, "m"))
            .build();
        GuiModule hitSound = GuiModule.toggle("Hit Sound", "Plays a local tone when a valid entity attack input begins.", () -> config.hitSound, value -> config.hitSound = value).build();
        GuiModule swing = GuiModule.toggle("Swing Speed", "Lengthens the local hand animation; more ticks means a slower swing.", () -> config.swingSpeed, value -> config.swingSpeed = value)
            .with(new GuiSetting.Slider("Slow duration", () -> config.swingDuration, value -> config.swingDuration = value, SwingDuration.MIN_TICKS, SwingDuration.MAX_TICKS, "t")).build();
        GuiModule combatHud = GuiModule.toggle("Combat HUD", "Shows attack cooldown and optional totem count near the crosshair.", () -> config.attackMeter, value -> config.attackMeter = value)
            .with(new GuiSetting.Toggle("Totem count", () -> config.totemCounter, value -> config.totemCounter = value)).build();
        return List.of(autoTotem, autoEat, autoArmor, smartWeapon, triggerBot, refill, spear, mace, safety, hitSound, swing, combatHud);
    }

    private static List<GuiModule> movement(ArcaneConfig config, ArcaneKeybinds keybinds, Minecraft client) {
        GuiModule sprint = GuiModule.toggle("Auto Sprint", "Sprints while moving forward when hunger and movement state allow it.", () -> config.autoSprint, value -> config.autoSprint = value).build();
        GuiModule walk = GuiModule.toggle("Auto Walk", "Holds forward only during gameplay and releases it for screens, disable, or world leave.", () -> config.autoWalk, value -> config.autoWalk = value)
            .with(new GuiSetting.Bind("Bind", keybinds.autoWalk())).build();
        GuiModule jump = GuiModule.toggle("Auto Jump", "Jumps while moving on the ground and never holds jump in a menu.", () -> config.autoJump, value -> config.autoJump = value).build();
        GuiModule sneak = GuiModule.toggle("Auto Sneak", "Holds sneak only during gameplay and releases it for screens, disable, or world leave.", () -> config.autoSneak, value -> config.autoSneak = value)
            .with(new GuiSetting.Bind("Bind", keybinds.autoSneak())).build();
        GuiModule inventoryMove = GuiModule.toggle("Inventory Move", "Passes physical movement keys through supported inventory screens; anvil text entry stays native.", () -> config.inventoryMove, value -> config.inventoryMove = value).build();
        GuiModule safeWalk = GuiModule.toggle("Safe Walk", "Suppresses edge movement while grounded and not intentionally jumping.", () -> config.safeWalk, value -> config.safeWalk = value).build();
        GuiModule parkour = GuiModule.toggle("Parkour Assist", "Times a jump near a block edge only while you are already moving forward.", () -> config.parkourAssist, value -> config.parkourAssist = value)
            .with(new GuiSetting.Slider("Edge distance", () -> config.parkourAssistWindow, value -> config.parkourAssistWindow = value, 1, 6, " step")).build();
        GuiModule swim = GuiModule.toggle("Swim Assist", "Applies upward swim input while moving through water without taking over dry-land movement.", () -> config.swimAssist, value -> config.swimAssist = value).build();
        GuiModule elytra = GuiModule.toggle("Elytra Assist", "Uses a rocket while gliding when speed drops, then restores the selected hotbar slot.", () -> config.elytraAssist, value -> config.elytraAssist = value)
            .with(new GuiSetting.Slider("Boost delay", () -> config.elytraAssistDelayTicks, value -> config.elytraAssistDelayTicks = value, 10, 100, "t"))
            .with(new GuiSetting.Toggle("Smart conservation", () -> config.elytraAssistSmartConservation, value -> config.elytraAssistSmartConservation = value))
            .with(new GuiSetting.Slider("Boost below", () -> config.elytraAssistBoostBelow, value -> config.elytraAssistBoostBelow = value, 5, 60, "m/s"))
            .with(new GuiSetting.Info("Rockets", () -> client.player == null ? "0" : Integer.toString(ElytraAssistController.rocketCount(client.player))))
            .build();
        GuiModule perspective = GuiModule.toggle("Elytra Perspective", "Switches from first-person to rear third-person while gliding, then restores the previous view.", () -> config.elytraPerspective, value -> config.elytraPerspective = value).build();
        GuiModule cruise = GuiModule.toggle("Vehicle Cruise", "Maintains forward vehicle input while mounted and releases it immediately outside gameplay.", () -> config.vehicleCruise, value -> config.vehicleCruise = value).build();
        return List.of(sprint, walk, jump, sneak, inventoryMove, safeWalk, parkour, swim, elytra, perspective, cruise);
    }

    private static List<GuiModule> render(ArcaneConfig config, ArcaneKeybinds keybinds, Minecraft client) {
        GuiModule radar = GuiModule.toggle("Base Radar", "Shows the scored-chunk grid around the player.", () -> config.hud, value -> config.hud = value).build();
        GuiModule freecam = GuiModule.toggle("Freecam", "Detaches the camera for local flight while interaction stays anchored to the real player.", FreecamController::isActive, value -> {
            if (value != FreecamController.isActive()) {
                FreecamController.toggle(client);
                closeCameraMenu(client);
            }
        })
            .with(new GuiSetting.Slider("Speed", () -> config.freecamSpeed, value -> config.freecamSpeed = value, FreecamSpeed.MIN, FreecamSpeed.MAX, ""))
            .with(new GuiSetting.Toggle("Directional mining", () -> config.freecamMining, value -> config.freecamMining = value))
            .with(new GuiSetting.Bind("Bind", keybinds.freecam())).build();
        GuiModule freelook = GuiModule.toggle("Freelook", "Orbits around your player while movement and interaction continue from the real view direction.", FreelookController::isActive, value -> {
            if (value != FreelookController.isActive()) {
                FreelookController.toggle(client);
                closeCameraMenu(client);
            }
        })
            .with(new GuiSetting.Toggle("Through walls", () -> config.freelookThroughWalls, value -> config.freelookThroughWalls = value))
            .with(new GuiSetting.Bind("Bind", keybinds.freelook())).build();
        GuiModule fullbright = GuiModule.toggle("Fullbright", "Applies client-side night-vision brightness without changing world blocks.", () -> config.fullbright, value -> config.fullbright = value).build();
        GuiModule hurtCam = GuiModule.toggle("No Hurt Cam", "Removes the damage tilt without changing damage feedback.", () -> config.noHurtCam, value -> config.noHurtCam = value).build();
        GuiModule zoom = GuiModule.toggle("Zoom", "Reduces client FOV while the zoom bind is held.", () -> config.zoom, value -> config.zoom = value)
            .with(new GuiSetting.Slider("FOV scale", () -> config.zoomPercent, value -> config.zoomPercent = value, 10, 90, "%"))
            .with(new GuiSetting.Bind("Hold bind", keybinds.zoom())).build();
        GuiModule clean = GuiModule.toggle("Clean Capture", "Hides Arcane overlays for screenshots and recordings.", () -> config.cleanCapture, value -> config.cleanCapture = value)
            .with(new GuiSetting.Bind("Bind", keybinds.cleanCapture())).build();
        GuiModule noRender = GuiModule.toggle("No Render", "Suppresses fire, underwater, in-wall, weather, and damage-camera tilt effects.", () -> config.noRender, value -> config.noRender = value).build();
        GuiModule waypoints = GuiModule.toggle("Waypoints", "Renders per-server and dimension markers managed by /arcane waypoint add, remove, or list.", () -> config.waypoints, value -> config.waypoints = value)
            .with(new GuiSetting.Swatch("Color", () -> config.waypointColor, color -> config.waypointColor = color))
            .with(new GuiSetting.Bind("Add bind", keybinds.waypoint())).build();
        GuiModule breadcrumbs = GuiModule.toggle("Breadcrumbs", "Keeps a bounded trail of your actual movement and resets it across dimensions.", () -> config.breadcrumbs, value -> config.breadcrumbs = value)
            .with(new GuiSetting.Slider("Trail points", () -> config.breadcrumbLength, value -> config.breadcrumbLength = value, 32, 1024, ""))
            .with(new GuiSetting.Swatch("Color", () -> config.breadcrumbColor, color -> config.breadcrumbColor = color)).build();
        GuiModule crosshair = GuiModule.toggle("Custom Crosshair", "Draws a movement-reactive crosshair with attack-ready feedback without changing hit detection.", () -> config.customCrosshair, value -> config.customCrosshair = value)
            .with(new GuiSetting.Slider("Size", () -> config.crosshairSize, value -> config.crosshairSize = value, 1, 15, "px"))
            .with(new GuiSetting.Slider("Gap", () -> config.crosshairGap, value -> config.crosshairGap = value, 0, 10, "px"))
            .with(new GuiSetting.Swatch("Color", () -> config.crosshairColor, color -> config.crosshairColor = color)).build();
        GuiModule chunkBorders = GuiModule.toggle("Chunk Borders", "Draws the current chunk footprint and its vertical corners.", () -> config.chunkBorders, value -> config.chunkBorders = value)
            .with(new GuiSetting.Swatch("Color", () -> config.chunkBorderColor, color -> config.chunkBorderColor = color)).build();
        return List.of(radar, freecam, freelook, fullbright, hurtCam, zoom, clean, noRender, waypoints, breadcrumbs, crosshair, chunkBorders);
    }

    private static void closeCameraMenu(Minecraft client) {
        if (client.gui.screen() instanceof ArcaneSettingsScreen) client.gui.setScreen(null);
    }

    private static List<GuiModule> utility(ArcaneConfig config, ArcaneKeybinds keybinds) {
        GuiModule autoTool = GuiModule.toggle("Auto Tool", "Selects the best hotbar tool before mining, then restores your slot.", () -> config.autoTool, value -> config.autoTool = value)
            .with(new GuiSetting.Toggle("Protect 1 durability", () -> config.autoToolPreserveDurability, value -> config.autoToolPreserveDurability = value)).build();
        GuiModule.Builder macros = GuiModule.toggle("Chat Macros", "Sends four saved messages, each on its own key.", () -> config.chatMacros, value -> config.chatMacros = value);
        List<KeyMapping> macroKeys = keybinds.chatMacros();
        for (int slot = 0; slot < macroKeys.size(); slot++) macros.with(new GuiSetting.Message(Integer.toString(slot + 1), slot, macroKeys.get(slot)));
        GuiModule autoRespawn = GuiModule.toggle("Auto Respawn", "Returns to play immediately after death without clicking the death screen.", () -> config.autoRespawn, value -> config.autoRespawn = value).build();
        GuiModule antiAfk = GuiModule.toggle("Anti AFK", "Performs one harmless hand swing after the configured idle interval.", () -> config.antiAfk, value -> config.antiAfk = value)
            .with(new GuiSetting.Slider("Idle interval", () -> config.antiAfkSeconds, value -> config.antiAfkSeconds = value, 30, 600, "s")).build();
        GuiModule durabilityGuard = GuiModule.toggle("Durability Guard", "Releases attack and use before the held item breaks.", () -> config.durabilityGuard, value -> config.durabilityGuard = value)
            .with(new GuiSetting.Slider("Stop at", () -> config.durabilityGuardRemaining, value -> config.durabilityGuardRemaining = value, 1, 25, " uses")).build();
        GuiModule deathTracker = GuiModule.toggle("Death Tracker", "Records the latest death in the current server session and optionally points back to it.", () -> config.deathCoordinates, value -> config.deathCoordinates = value)
            .with(new GuiSetting.Toggle("Status HUD beacon", () -> config.hudDeathBeacon, value -> config.hudDeathBeacon = value)).build();
        GuiModule coordinateClipboard = GuiModule.toggle("Copy Coordinates", "Copies exact block coordinates with a bind and obeys Streamer Mode.", () -> config.coordinateClipboard, value -> config.coordinateClipboard = value)
            .with(new GuiSetting.Bind("Copy bind", keybinds.coordinateClipboard())).build();
        GuiModule autoFish = GuiModule.toggle("Auto Fish", "Recasts only after the local fishing-bobber bite signal and explicit opt-in.", () -> config.autoFish, value -> config.autoFish = value).build();
        return List.of(autoTool, macros.build(), autoRespawn, antiAfk, durabilityGuard, deathTracker, coordinateClipboard, autoFish);
    }

    private static List<GuiModule> clientTools(ArcaneConfig config, ArcaneKeybinds keybinds, Minecraft client) {
        GuiModule performance = GuiModule.value("Performance", "Applies fixed scan and render work-budget presets.", () -> config.performanceProfile().label())
            .with(new GuiSetting.Cycle("Profile", () -> config.performanceProfile().label(), () -> {
                config.cyclePerformanceProfile();
                ArcaneClient.engine().settingsChanged(client);
            }))
            .with(new GuiSetting.Info("Frame rate", () -> client.getFps() + " FPS")).build();

        GuiModule interfaceModule = GuiModule.value("Interface", "Glass, colors and layout. Shift-click a module to favorite it.", () -> config.customUiColors ? "CUSTOM" : ClickGuiTheme.fromConfig(config.uiTheme).label())
            .with(new GuiSetting.Cycle("Theme", () -> ClickGuiTheme.fromConfig(config.uiTheme).label(), () -> config.uiTheme = (config.uiTheme + 1) % ClickGuiTheme.count()))
            .with(new GuiSetting.Slider("Glass opacity", () -> config.uiOpacityPercent, value -> config.uiOpacityPercent = value, 45, 100, "%"))
            .with(new GuiSetting.Toggle("World blur", () -> config.uiBlur, value -> config.uiBlur = value))
            .with(new GuiSetting.Toggle("Smooth renderer", () -> config.uiVectorRendering, value -> config.uiVectorRendering = value))
            .with(new GuiSetting.Cycle("Font", () -> config.uiFont == 0 ? "Condensed" : "Sora", () -> config.uiFont = 1 - config.uiFont))
            .with(new GuiSetting.Toggle("Reduced motion", () -> config.uiReducedMotion, value -> config.uiReducedMotion = value))
            .with(new GuiSetting.Toggle("One settings section", () -> config.uiSingleSettings, value -> config.uiSingleSettings = value))
            .with(new GuiSetting.Slider("GUI scale", () -> config.uiScalePercent, value -> config.uiScalePercent = value, 75, 125, "%"))
            .with(new GuiSetting.Slider("Density", () -> config.uiDensityPercent, value -> config.uiDensityPercent = value, 75, 150, "%"))
            .with(new GuiSetting.Cycle("Reset layout", () -> "Reset", () -> {
                if (client.gui.screen() instanceof ArcaneSettingsScreen screen) screen.resetPanelLayout();
            }))
            .with(new GuiSetting.Slider("Corner radius", () -> config.uiCornerRadius, value -> config.uiCornerRadius = value, 0, 12, "px"))
            .with(new GuiSetting.Slider("Animation", () -> config.uiAnimationPercent, value -> config.uiAnimationPercent = value, 0, 150, "%"))
            .with(new GuiSetting.Slider("Background dim", () -> config.uiBackgroundDimPercent, value -> config.uiBackgroundDimPercent = value, 0, 80, "%"))
            .with(new GuiSetting.Slider("Status panels scale", () -> config.hudScalePercent, value -> config.hudScalePercent = value, 70, 140, "%"))
            .with(new GuiSetting.Cycle("Status panels anchor", () -> hudAnchorLabel(config.hudAnchor), () -> config.hudAnchor = (config.hudAnchor + 1) % 4))
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

        GuiModule statusHud = GuiModule.toggle("Status HUD", "One configurable panel for survival, target, effect, flight, mount, and death-return status.", () -> config.statusHud, value -> config.statusHud = value)
            .with(new GuiSetting.Toggle("Health", () -> config.hudHealth, value -> config.hudHealth = value))
            .with(new GuiSetting.Toggle("Hunger", () -> config.hudHunger, value -> config.hudHunger = value))
            .with(new GuiSetting.Toggle("Armor", () -> config.hudArmor, value -> config.hudArmor = value))
            .with(new GuiSetting.Toggle("Air", () -> config.hudAir, value -> config.hudAir = value))
            .with(new GuiSetting.Toggle("Experience", () -> config.hudExperience, value -> config.hudExperience = value))
            .with(new GuiSetting.Toggle("Target", () -> config.hudTarget, value -> config.hudTarget = value))
            .with(new GuiSetting.Toggle("Elytra", () -> config.hudElytra, value -> config.hudElytra = value))
            .with(new GuiSetting.Toggle("Mount", () -> config.hudMount, value -> config.hudMount = value))
            .with(new GuiSetting.Toggle("Potion timers", () -> config.hudPotionTimers, value -> config.hudPotionTimers = value))
            .with(new GuiSetting.Toggle("Death beacon", () -> config.hudDeathBeacon, value -> config.hudDeathBeacon = value))
            .build();
        GuiModule inventoryHud = GuiModule.toggle("Inventory HUD", "One inventory pass supplies held durability, useful-item totals, and empty-slot count.", () -> config.inventoryHud, value -> config.inventoryHud = value)
            .with(new GuiSetting.Toggle("Held durability", () -> config.hudHeldDurability, value -> config.hudHeldDurability = value))
            .with(new GuiSetting.Toggle("Totems", () -> config.hudTotems, value -> config.hudTotems = value))
            .with(new GuiSetting.Toggle("Rockets", () -> config.hudRockets, value -> config.hudRockets = value))
            .with(new GuiSetting.Toggle("Pearls", () -> config.hudPearls, value -> config.hudPearls = value))
            .with(new GuiSetting.Toggle("Golden apples", () -> config.hudGapples, value -> config.hudGapples = value))
            .with(new GuiSetting.Toggle("Crystals", () -> config.hudCrystals, value -> config.hudCrystals = value))
            .with(new GuiSetting.Toggle("Arrows", () -> config.hudArrows, value -> config.hudArrows = value))
            .with(new GuiSetting.Toggle("Empty slots", () -> config.hudInventorySpace, value -> config.hudInventorySpace = value))
            .build();
        GuiModule sounds = GuiModule.toggle("Sound Notifications", "Master volume for combat and safety notification tones.", () -> config.soundNotifications, value -> config.soundNotifications = value)
            .with(new GuiSetting.Slider("Volume", () -> config.notificationVolume, value -> config.notificationVolume = value, 0, 100, "%")).build();
        GuiModule streamer = GuiModule.toggle("Streamer Mode", "Redacts coordinates and player names from Arcane overlays.", () -> config.streamerMode, value -> config.streamerMode = value).build();
        GuiModule relog = GuiModule.value(
            "Relog",
            "Disconnects and rejoins the same server after two seconds. No extra mod needed.",
            RelogController::statusLabel
        )
            .with(new GuiSetting.Bind("Bind", keybinds.relog()))
            .build();
        return List.of(performance, interfaceModule, infoHud, statusHud, inventoryHud, sounds, streamer, relog);
    }

    private static String hudAnchorLabel(int anchor) {
        return switch (Math.clamp(anchor, 0, 3)) {
            case 0 -> "TOP LEFT";
            case 1 -> "TOP RIGHT";
            case 2 -> "BOTTOM LEFT";
            default -> "BOTTOM RIGHT";
        };
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
