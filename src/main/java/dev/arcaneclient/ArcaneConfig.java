package dev.arcaneclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.chat.ChatMacroMessage;
import dev.arcaneclient.combat.SwingDuration;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.freecam.FreecamSpeed;
import dev.arcaneclient.performance.PerformanceProfile;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(value=EnvType.CLIENT)
public final class ArcaneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_CONFIG_VERSION = 14;
    public boolean enabled = true;
    public int welcomeNoticeVersion = 0;
    public boolean overlay = false;
    public boolean hud = true;
    public boolean esp = false;
    public boolean storageTracers = false;
    public boolean storageChatAlerts = false;
    public boolean blockEntityDebug = false;
    public boolean itemTracers = true;
    public boolean itemEsp = false;
    public boolean tunnelEsp = false;
    /** Optional detail panel; the base radar is the only HUD enabled by default. */
    public boolean chunkAnalysis = false;
    public boolean autoTotem = false;
    public boolean chatMacros = true;

    // Combat and survival modules.
    public boolean autoSprint = false;
    public boolean autoEat = false;
    public boolean hitSound = false;
    public boolean swingSpeed = false;
    public boolean attackMeter = false;
    public boolean totemCounter = false;

    // Focused combat automation. Risky actions are always opt-in.
    public boolean autoArmor = false;
    public boolean smartWeapon = false;
    public boolean triggerBot = false;
    public boolean hotbarRefill = false;
    public boolean spearSwitch = false;
    public boolean maceSwitch = false;
    public boolean safetyDisconnect = false;
    public boolean safetyRuleHealth = true;
    public boolean safetyRuleTotems = true;
    public boolean safetyRuleArmor = true;
    public boolean safetyRuleFlight = true;
    public boolean safetyRuleProximity = false;

    // Utility and information modules.
    public boolean autoTool = false;
    public boolean autoToolPreserveDurability = true;
    public boolean elytraAssist = false;
    public boolean elytraAssistSmartConservation = true;
    public boolean infoHud = false;
    public boolean infoFps = true;
    public boolean infoCoordinates = true;
    public boolean infoDirection = true;
    public boolean infoSpeed = true;
    public boolean infoPing = true;
    public boolean infoBiome = true;

    // General quality-of-life automation. Every forced input is released when a screen opens or the world changes.
    public boolean autoRespawn = false;
    public boolean antiAfk = false;
    public boolean autoWalk = false;
    public boolean autoJump = false;
    public boolean autoSneak = false;
    public boolean durabilityGuard = false;
    public boolean deathCoordinates = false;
    public boolean elytraPerspective = false;
    public boolean coordinateClipboard = false;
    // Focused movement assistance.
    public boolean inventoryMove = false;
    public boolean safeWalk = false;
    public boolean parkourAssist = false;
    public boolean swimAssist = false;
    public boolean vehicleCruise = false;

    // Grouped Status/Inventory HUD sub-lines.
    public boolean hudHealth = true;
    public boolean hudHunger = true;
    public boolean hudArmor = true;
    public boolean hudAir = false;
    public boolean hudExperience = false;
    public boolean hudHeldDurability = true;
    public boolean hudTotems = true;
    public boolean hudRockets = true;
    public boolean hudPearls = false;
    public boolean hudGapples = false;
    public boolean hudCrystals = false;
    public boolean hudArrows = false;
    public boolean hudInventorySpace = true;
    public boolean hudTarget = false;
    public boolean hudElytra = false;
    public boolean hudMount = false;
    public boolean hudPotionTimers = true;
    public boolean hudDeathBeacon = false;
    public boolean statusHud = false;
    public boolean inventoryHud = false;

    // Entity and terrain ESP modules.
    public boolean playerEsp = false;
    public boolean mobEsp = false;
    public boolean projectileEsp = false;
    public boolean crystalEsp = false;
    public boolean entityTracers = false;
    public boolean holeEsp = false;
    public boolean searchEsp = false;
    public boolean nametags = false;
    public boolean logoutSpots = false;
    public boolean portalEsp = false;

    // Camera, media, and client presentation modules.
    public boolean fullbright = false;
    public boolean noHurtCam = false;
    public boolean zoom = false;
    public boolean cleanCapture = false;
    public boolean soundNotifications = true;
    public boolean streamerMode = false;
    public boolean freecamMining = true;
    public boolean freelookThroughWalls = false;
    public boolean customUiColors = false;
    public boolean noRender = false;
    public boolean waypoints = false;
    public boolean breadcrumbs = false;
    public boolean customCrosshair = false;
    public boolean chunkBorders = false;
    public boolean autoFish = false;

    // Distinct scanner evidence channels exposed as Chunk Finder settings.
    public boolean growthChronicle = true;
    public boolean harvestRhythm = true;
    public boolean farmGeometry = true;
    public boolean automationCadence = true;
    public boolean managedHabitats = true;
    public boolean evidenceConstellation = true;
    public boolean evidencePoints = false;
    public boolean clusterInference = true;
    public boolean packetSignals = true;
    public boolean playerBlockSignals = true;
    public boolean machineSignals = true;
    public boolean deepFocus = true;
    public boolean amethystEsp = false;
    public boolean accessTrailEsp = false;
    public boolean itemEspTotems = true;
    public boolean itemEspCrystals = true;
    public boolean itemEspElytra = true;
    public boolean itemEspShulkers = true;
    public boolean itemEspGapples = true;
    public boolean itemEspValuables = true;
    public int itemEspTotemColor = ItemEspCategory.TOTEMS.defaultColor();
    public int itemEspCrystalColor = ItemEspCategory.CRYSTALS.defaultColor();
    public int itemEspElytraColor = ItemEspCategory.ELYTRA.defaultColor();
    public int itemEspShulkerColor = ItemEspCategory.SHULKERS.defaultColor();
    public int itemEspGappleColor = ItemEspCategory.GAPPLES.defaultColor();
    public int itemEspValuableColor = ItemEspCategory.VALUABLES.defaultColor();
    public int tunnelTwoByOneColor = -229263105;
    public int tunnelThreeByThreeColor = -218115499;
    public int amethystEspRange = 192;
    public int accessTrailEspRange = 192;
    public int amethystEspColor = 0xFFC084FC;
    public int accessTrailEspColor = 0xFF94A3B8;
    public int threshold = 35;
    public int scanRadius = 12;
    public int chunksPerTick = 8;
    public int rescanSeconds = 30;
    public int uiTheme = 0;
    public int performanceProfile = 0;
    public int configVersion = CURRENT_CONFIG_VERSION;
    public int freecamSpeed = 8;
    public int autoEatHunger = 8;
    public int elytraAssistDelayTicks = 40;
    public int elytraAssistBoostBelow = 24;
    public int lowHealthHearts = 4;
    public int armorAlertPercent = 15;
    public int autoArmorDelayTicks = 4;
    public int smartWeaponDelayTicks = 1;
    public int triggerBotDelayTicks = 2;
    public int hotbarRefillThreshold = 16;
    public int safetyTotemMinimum = 0;
    public int antiAfkSeconds = 120;
    public int durabilityGuardRemaining = 3;
    public int nearbyPlayerRange = 64;
    public int flightSafetyDurability = 15;
    public int flightSafetyRockets = 8;
    public int parkourAssistWindow = 3;
    public int searchEspRange = 96;
    public int nametagRange = 96;
    public int logoutSpotMinutes = 30;
    public int portalEspRange = 128;
    public int breadcrumbLength = 256;
    public int crosshairSize = 5;
    public int crosshairGap = 3;
    public int searchEspColor = 0xFF22D3EE;
    public int nametagColor = 0xFFFFFFFF;
    public int logoutSpotColor = 0xFFF59E0B;
    public int portalEspColor = 0xFFC084FC;
    public int waypointColor = 0xFF76A9FF;
    public int breadcrumbColor = 0xFF34D399;
    public int crosshairColor = 0xFFFFFFFF;
    public int chunkBorderColor = 0xFF9A8CFF;
    public int swingDuration = SwingDuration.DEFAULT_TICKS;
    public int zoomPercent = 35;
    public int notificationVolume = 70;
    public int entityEspRange = 96;
    public int holeEspRange = 8;
    public int playerEspColor = 0xFF76A9FF;
    public int mobEspColor = 0xFFF08AA0;
    public int projectileEspColor = 0xFFF59E0B;
    public int crystalEspColor = 0xFFC084FC;
    public int holeEspColor = 0xFF34D399;
    public int uiAccentColor = 0xFF9A8CFF;
    public int uiPanelColor = 0xFF121317;
    public int uiTextColor = 0xFFF4F4F5;
    public int uiScalePercent = 100;
    /** Higher values mean tighter rows and more modules visible at once. */
    public int uiDensityPercent = 100;
    public int uiOpacityPercent = 100;
    public int devUiRevision = 0;
    public int uiCornerRadius = 3;
    public int uiAnimationPercent = 100;
    public int uiBackgroundDimPercent = 42;
    public int hudScalePercent = 100;
    /** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    public int hudAnchor = 1;
    public String chatMacro1 = "";
    public String chatMacro2 = "";
    public String chatMacro3 = "";
    public String chatMacro4 = "";

    public static ArcaneConfig load() {
        Path path = path();
        if (!Files.isRegularFile(path)) {
            ArcaneConfig config = new ArcaneConfig();
            config.save();
            return config;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            ArcaneConfig config = GSON.fromJson(reader, ArcaneConfig.class);
            if (config == null) {
                config = new ArcaneConfig();
            }

            boolean upgradedSpeedDefaults = config.chunksPerTick == 1 && config.rescanSeconds == 45;
            if (upgradedSpeedDefaults) {
                config.chunksPerTick = 3;
                config.rescanSeconds = 30;
            }
            int loadedConfigVersion = config.configVersion;
            boolean upgradedFeatureDefaults = loadedConfigVersion < 3;
            boolean upgradedSlowSwingAndInfo = loadedConfigVersion < 4;
            boolean upgradedGrowthScanner = loadedConfigVersion < 5;
            boolean upgradedStorageDiscovery = loadedConfigVersion < 6;
            boolean upgradedElytraAssist = loadedConfigVersion < 7;
            boolean upgradedExpandedModules = loadedConfigVersion < 8;
            boolean upgradedExactCatalog = loadedConfigVersion < 9;
            boolean upgradedFocusedCatalog = loadedConfigVersion < 10;
            boolean upgradedObservationEsp = loadedConfigVersion < 11;
            boolean upgradedUndergroundEntityDebug = loadedConfigVersion < 12;
            boolean upgradedLegacyBaseFinder = loadedConfigVersion < 13;
            boolean upgradedUiPolish = loadedConfigVersion < 14;
            if (upgradedFeatureDefaults) {
                config.soundNotifications = true;
                config.freecamMining = true;
                config.freecamSpeed = 8;
                config.autoEatHunger = 8;
                config.lowHealthHearts = 4;
                config.armorAlertPercent = 15;
                config.swingDuration = SwingDuration.DEFAULT_TICKS;
                config.zoomPercent = 35;
                config.notificationVolume = 70;
                config.entityEspRange = 96;
                config.holeEspRange = 8;
                config.playerEspColor = 0xFF76A9FF;
                config.mobEspColor = 0xFFF08AA0;
                config.projectileEspColor = 0xFFF59E0B;
                config.crystalEspColor = 0xFFC084FC;
                config.holeEspColor = 0xFF34D399;
                config.uiAccentColor = 0xFF9A8CFF;
                config.uiPanelColor = 0xFF121317;
                config.uiTextColor = 0xFFF4F4F5;
            }
            if (upgradedSlowSwingAndInfo) {
                config.swingDuration = SwingDuration.DEFAULT_TICKS;
                config.autoTool = false;
                config.autoToolPreserveDurability = true;
                config.infoHud = true;
                config.infoFps = true;
                config.infoCoordinates = true;
                config.infoDirection = true;
                config.infoSpeed = true;
                config.infoPing = true;
                config.infoBiome = true;
            }
            if (upgradedGrowthScanner && config.chunksPerTick == 3) {
                config.chunksPerTick = 8;
            }
            if (upgradedStorageDiscovery) {
                config.storageChatAlerts = true;
            }
            if (upgradedElytraAssist) {
                config.elytraAssist = false;
                config.elytraAssistSmartConservation = true;
                config.elytraAssistDelayTicks = 40;
                config.elytraAssistBoostBelow = 24;
            }
            if (upgradedExpandedModules) {
                config.antiAfkSeconds = 120;
                config.durabilityGuardRemaining = 3;
                config.nearbyPlayerRange = 64;
                config.flightSafetyDurability = 15;
                config.flightSafetyRockets = 8;
                config.uiScalePercent = 100;
                config.uiDensityPercent = 100;
                config.uiOpacityPercent = 88;
                config.uiCornerRadius = 5;
                config.uiAnimationPercent = 100;
                config.uiBackgroundDimPercent = 42;
                config.hudScalePercent = 100;
                config.hudAnchor = 1;
            }
            if (upgradedExactCatalog) {
                // Never opt users into automation or disconnect behavior during migration.
                config.autoArmor = false;
                config.smartWeapon = false;
                config.triggerBot = false;
                config.hotbarRefill = false;
                config.spearSwitch = false;
                config.maceSwitch = false;
                config.safetyDisconnect = false;
                config.inventoryMove = false;
                config.safeWalk = false;
                config.parkourAssist = false;
                config.swimAssist = false;
                config.vehicleCruise = false;
                config.searchEsp = false;
                config.nametags = false;
                config.logoutSpots = false;
                config.portalEsp = false;
                config.noRender = false;
                config.waypoints = false;
                config.breadcrumbs = false;
                config.customCrosshair = false;
                config.chunkBorders = false;
                config.autoFish = false;

                config.safetyRuleHealth = true;
                config.safetyRuleTotems = true;
                config.safetyRuleArmor = true;
                config.safetyRuleFlight = true;
                config.safetyRuleProximity = false;
                config.safetyTotemMinimum = 0;
                // The v8 catalog exposed many one-line HUD toggles. Do not carry
                // that clutter forward implicitly: the two grouped panels remain
                // available, but users opt into either panel deliberately.
                config.chunkAnalysis = false;
                config.statusHud = false;
                config.inventoryHud = false;

                config.growthChronicle = true;
                config.harvestRhythm = true;
                config.farmGeometry = true;
                config.automationCadence = true;
                config.managedHabitats = true;
                config.evidenceConstellation = true;
            }
            if (upgradedFocusedCatalog) {
                // Replace the old everything-on presentation with one useful scanner readout.
                // Every secondary overlay remains available, but must be deliberately enabled.
                config.overlay = false;
                config.esp = false;
                config.storageTracers = false;
                config.storageChatAlerts = false;
                config.infoHud = false;
                config.chunkAnalysis = false;
                config.statusHud = false;
                config.inventoryHud = false;
                config.attackMeter = false;
            }
            if (upgradedObservationEsp) {
                config.amethystEsp = false;
                config.accessTrailEsp = false;
                config.amethystEspRange = 192;
                config.accessTrailEspRange = 192;
                config.amethystEspColor = 0xFFC084FC;
                config.accessTrailEspColor = 0xFF94A3B8;
            }
            if (upgradedUndergroundEntityDebug) {
                // Restored as a separate opt-in layer; collection itself enforces Y < 0.
                config.blockEntityDebug = false;
            }
            if (upgradedLegacyBaseFinder) {
                config.packetSignals = true;
                config.playerBlockSignals = true;
                config.machineSignals = true;
                config.deepFocus = true;
            }
            if (upgradedUiPolish) {
                // Preserve deliberate customization while upgrading the previous untouched defaults.
                if (config.uiOpacityPercent == 88) config.uiOpacityPercent = 96;
                if (config.uiCornerRadius == 5) config.uiCornerRadius = 3;
            }
            if (upgradedFeatureDefaults || upgradedSlowSwingAndInfo || upgradedGrowthScanner || upgradedStorageDiscovery || upgradedElytraAssist || upgradedExpandedModules || upgradedExactCatalog || upgradedFocusedCatalog || upgradedObservationEsp || upgradedUndergroundEntityDebug || upgradedLegacyBaseFinder || upgradedUiPolish) {
                config.configVersion = CURRENT_CONFIG_VERSION;
            }

            config.clamp();
            if (upgradedSpeedDefaults || upgradedFeatureDefaults || upgradedSlowSwingAndInfo || upgradedGrowthScanner || upgradedStorageDiscovery || upgradedElytraAssist || upgradedExpandedModules || upgradedExactCatalog || upgradedFocusedCatalog || upgradedObservationEsp || upgradedUndergroundEntityDebug || upgradedLegacyBaseFinder || upgradedUiPolish) {
                config.save();
            }
            return config;
        } catch (IOException | RuntimeException exception) {
            ArcaneClient.LOGGER.warn("Could not read {}; using defaults", path, exception);
            return new ArcaneConfig();
        }
    }

    public void save() {
        this.clamp();
        Path path = ArcaneConfig.path();
        try {
            dev.arcaneclient.config.AtomicConfigFile.write(path, GSON.toJson(this));
        }
        catch (IOException exception) {
            ArcaneClient.LOGGER.warn("Could not write {}", (Object)path, (Object)exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("arcane-client.json");
    }

    public PerformanceProfile performanceProfile() {
        return PerformanceProfile.fromConfig(this.performanceProfile);
    }

    public void cyclePerformanceProfile() {
        this.performanceProfile = this.performanceProfile().next().ordinal();
    }

    public int sensitivity() {
        return 100 - this.threshold;
    }

    public void setSensitivity(int sensitivity) {
        this.threshold = 100 - Math.clamp((long)sensitivity, 0, 100);
    }

    public boolean itemEspEnabled(ItemEspCategory category) {
        return switch (category) {
            default -> throw new MatchException(null, null);
            case ItemEspCategory.TOTEMS -> this.itemEspTotems;
            case ItemEspCategory.CRYSTALS -> this.itemEspCrystals;
            case ItemEspCategory.ELYTRA -> this.itemEspElytra;
            case ItemEspCategory.SHULKERS -> this.itemEspShulkers;
            case ItemEspCategory.GAPPLES -> this.itemEspGapples;
            case ItemEspCategory.VALUABLES -> this.itemEspValuables;
        };
    }

    public void setItemEspEnabled(ItemEspCategory category, boolean enabled) {
        switch (category) {
            case TOTEMS: {
                this.itemEspTotems = enabled;
                break;
            }
            case CRYSTALS: {
                this.itemEspCrystals = enabled;
                break;
            }
            case ELYTRA: {
                this.itemEspElytra = enabled;
                break;
            }
            case SHULKERS: {
                this.itemEspShulkers = enabled;
                break;
            }
            case GAPPLES: {
                this.itemEspGapples = enabled;
                break;
            }
            case VALUABLES: {
                this.itemEspValuables = enabled;
            }
        }
    }

    public int itemEspColor(ItemEspCategory category) {
        return switch (category) {
            default -> throw new MatchException(null, null);
            case ItemEspCategory.TOTEMS -> this.itemEspTotemColor;
            case ItemEspCategory.CRYSTALS -> this.itemEspCrystalColor;
            case ItemEspCategory.ELYTRA -> this.itemEspElytraColor;
            case ItemEspCategory.SHULKERS -> this.itemEspShulkerColor;
            case ItemEspCategory.GAPPLES -> this.itemEspGappleColor;
            case ItemEspCategory.VALUABLES -> this.itemEspValuableColor;
        };
    }

    public void setItemEspColor(ItemEspCategory category, int color) {
        switch (category) {
            case TOTEMS: {
                this.itemEspTotemColor = color;
                break;
            }
            case CRYSTALS: {
                this.itemEspCrystalColor = color;
                break;
            }
            case ELYTRA: {
                this.itemEspElytraColor = color;
                break;
            }
            case SHULKERS: {
                this.itemEspShulkerColor = color;
                break;
            }
            case GAPPLES: {
                this.itemEspGappleColor = color;
                break;
            }
            case VALUABLES: {
                this.itemEspValuableColor = color;
            }
        }
    }

    public String chatMacro(int index) {
        return switch (index) {
            case 0 -> this.chatMacro1;
            case 1 -> this.chatMacro2;
            case 2 -> this.chatMacro3;
            case 3 -> this.chatMacro4;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public void setChatMacro(int index, String value) {
        String normalized = ChatMacroMessage.normalize(value);
        switch (index) {
            case 0: {
                this.chatMacro1 = normalized;
                break;
            }
            case 1: {
                this.chatMacro2 = normalized;
                break;
            }
            case 2: {
                this.chatMacro3 = normalized;
                break;
            }
            case 3: {
                this.chatMacro4 = normalized;
                break;
            }
            default: {
                throw new IndexOutOfBoundsException(index);
            }
        }
    }

    public static int channel(int color, int shift) {
        return color >> shift & 0xFF;
    }

    public static int withChannel(int color, int shift, int value) {
        int clamped = Math.clamp(value, 0, 255);
        return color & ~(0xFF << shift) | clamped << shift | 0xFF000000;
    }

    private void clamp() {
        this.threshold = Math.clamp((long)this.threshold, 0, 100);
        this.scanRadius = Math.clamp((long)this.scanRadius, 2, 24);
        this.chunksPerTick = Math.clamp((long)this.chunksPerTick, 1, 16);
        this.rescanSeconds = Math.clamp((long)this.rescanSeconds, 10, 300);
        this.uiTheme = Math.clamp(this.uiTheme, 0, 2);
        this.performanceProfile = Math.clamp(this.performanceProfile, 0, 2);
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.freecamSpeed = Math.clamp(this.freecamSpeed, FreecamSpeed.MIN, FreecamSpeed.MAX);
        this.autoEatHunger = Math.clamp(this.autoEatHunger, 1, 19);
        this.elytraAssistDelayTicks = Math.clamp(this.elytraAssistDelayTicks, 10, 100);
        this.elytraAssistBoostBelow = Math.clamp(this.elytraAssistBoostBelow, 5, 60);
        this.lowHealthHearts = Math.clamp(this.lowHealthHearts, 1, 10);
        this.armorAlertPercent = Math.clamp(this.armorAlertPercent, 1, 100);
        this.autoArmorDelayTicks = Math.clamp(this.autoArmorDelayTicks, 1, 20);
        this.smartWeaponDelayTicks = Math.clamp(this.smartWeaponDelayTicks, 0, 10);
        this.triggerBotDelayTicks = Math.clamp(this.triggerBotDelayTicks, 0, 20);
        this.hotbarRefillThreshold = Math.clamp(this.hotbarRefillThreshold, 1, 63);
        this.safetyTotemMinimum = Math.clamp(this.safetyTotemMinimum, 0, 8);
        this.antiAfkSeconds = Math.clamp(this.antiAfkSeconds, 30, 600);
        this.durabilityGuardRemaining = Math.clamp(this.durabilityGuardRemaining, 1, 25);
        this.nearbyPlayerRange = Math.clamp(this.nearbyPlayerRange, 8, 160);
        this.flightSafetyDurability = Math.clamp(this.flightSafetyDurability, 1, 50);
        this.flightSafetyRockets = Math.clamp(this.flightSafetyRockets, 1, 32);
        this.parkourAssistWindow = Math.clamp(this.parkourAssistWindow, 1, 8);
        this.searchEspRange = Math.clamp(this.searchEspRange, 16, 192);
        this.amethystEspRange = Math.clamp(this.amethystEspRange, 16, 384);
        this.accessTrailEspRange = Math.clamp(this.accessTrailEspRange, 16, 384);
        this.nametagRange = Math.clamp(this.nametagRange, 16, 192);
        this.logoutSpotMinutes = Math.clamp(this.logoutSpotMinutes, 1, 120);
        this.portalEspRange = Math.clamp(this.portalEspRange, 16, 192);
        this.breadcrumbLength = Math.clamp(this.breadcrumbLength, 32, 1024);
        this.crosshairSize = Math.clamp(this.crosshairSize, 1, 15);
        this.crosshairGap = Math.clamp(this.crosshairGap, 0, 10);
        this.searchEspColor |= 0xFF000000;
        this.nametagColor |= 0xFF000000;
        this.logoutSpotColor |= 0xFF000000;
        this.portalEspColor |= 0xFF000000;
        this.waypointColor |= 0xFF000000;
        this.breadcrumbColor |= 0xFF000000;
        this.crosshairColor |= 0xFF000000;
        this.chunkBorderColor |= 0xFF000000;
        this.amethystEspColor |= 0xFF000000;
        this.accessTrailEspColor |= 0xFF000000;
        this.swingDuration = SwingDuration.clamp(this.swingDuration);
        this.zoomPercent = Math.clamp(this.zoomPercent, 10, 90);
        this.notificationVolume = Math.clamp(this.notificationVolume, 0, 100);
        this.entityEspRange = Math.clamp(this.entityEspRange, 16, 192);
        this.holeEspRange = Math.clamp(this.holeEspRange, 4, 16);
        this.uiScalePercent = Math.clamp(this.uiScalePercent, 75, 125);
        this.uiDensityPercent = Math.clamp(this.uiDensityPercent, 75, 150);
        this.uiOpacityPercent = Math.clamp(this.uiOpacityPercent, 45, 100);
        this.uiCornerRadius = Math.clamp(this.uiCornerRadius, 0, 12);
        this.uiAnimationPercent = Math.clamp(this.uiAnimationPercent, 0, 150);
        this.uiBackgroundDimPercent = Math.clamp(this.uiBackgroundDimPercent, 0, 80);
        this.hudScalePercent = Math.clamp(this.hudScalePercent, 70, 140);
        this.hudAnchor = Math.clamp(this.hudAnchor, 0, 3);
        this.playerEspColor |= 0xFF000000;
        this.mobEspColor |= 0xFF000000;
        this.projectileEspColor |= 0xFF000000;
        this.crystalEspColor |= 0xFF000000;
        this.holeEspColor |= 0xFF000000;
        this.uiAccentColor |= 0xFF000000;
        this.uiPanelColor |= 0xFF000000;
        this.uiTextColor |= 0xFF000000;
        this.chatMacro1 = ChatMacroMessage.normalize(this.chatMacro1);
        this.chatMacro2 = ChatMacroMessage.normalize(this.chatMacro2);
        this.chatMacro3 = ChatMacroMessage.normalize(this.chatMacro3);
        this.chatMacro4 = ChatMacroMessage.normalize(this.chatMacro4);
    }
}
