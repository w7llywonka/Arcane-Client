package dev.arcaneclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.chat.ChatMacroMessage;
import dev.arcaneclient.combat.SwingDuration;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.model.SignalCategory;
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
    private static final int CURRENT_CONFIG_VERSION = 4;
    public boolean enabled = true;
    public boolean overlay = true;
    public boolean hud = true;
    public boolean packetSignals = true;
    public boolean esp = true;
    public boolean storageTracers = true;
    public boolean itemTracers = true;
    public boolean blockEntityDebug = false;
    public boolean itemEsp = false;
    public boolean tunnelEsp = false;
    public boolean stashAlerts = true;
    public boolean chunkAnalysis = true;
    public boolean autoTotem = false;
    public boolean chatMacros = true;

    // Combat and survival modules.
    public boolean autoSprint = false;
    public boolean autoEat = false;
    public boolean lowHealthAlert = false;
    public boolean armorAlert = false;
    public boolean hitSound = false;
    public boolean swingSpeed = false;
    public boolean attackMeter = false;
    public boolean totemCounter = false;

    // Utility and information modules.
    public boolean autoTool = false;
    public boolean autoToolPreserveDurability = true;
    public boolean infoHud = true;
    public boolean infoFps = true;
    public boolean infoCoordinates = true;
    public boolean infoDirection = true;
    public boolean infoSpeed = true;
    public boolean infoPing = true;
    public boolean infoBiome = true;

    // Entity and terrain ESP modules.
    public boolean playerEsp = false;
    public boolean mobEsp = false;
    public boolean projectileEsp = false;
    public boolean crystalEsp = false;
    public boolean entityTracers = false;
    public boolean holeEsp = false;
    public boolean entityNameTags = true;

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
    public boolean farmSignals = true;
    public boolean playerBlockSignals = true;
    public boolean machineSignals = true;
    public boolean lightSignals = true;
    public boolean entitySignals = true;
    public boolean deepFocus = true;
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
    public int threshold = 35;
    public int scanRadius = 12;
    public int chunksPerTick = 3;
    public int rescanSeconds = 30;
    public int uiTheme = 0;
    public int performanceProfile = 0;
    public int configVersion = CURRENT_CONFIG_VERSION;
    public int freecamSpeed = 8;
    public int autoEatHunger = 8;
    public int lowHealthHearts = 4;
    public int armorAlertPercent = 15;
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
            if (upgradedFeatureDefaults) {
                config.entityNameTags = true;
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
            if (upgradedFeatureDefaults || upgradedSlowSwingAndInfo) {
                config.configVersion = CURRENT_CONFIG_VERSION;
            }

            config.clamp();
            if (upgradedSpeedDefaults || upgradedFeatureDefaults || upgradedSlowSwingAndInfo) {
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
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(path, new OpenOption[0]);){
                GSON.toJson((Object)this, (Appendable)writer);
            }
        }
        catch (IOException exception) {
            ArcaneClient.LOGGER.warn("Could not write {}", (Object)path, (Object)exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("arcane-client.json");
    }

    public boolean allows(SignalCategory category) {
        return switch (category) {
            default -> throw new MatchException(null, null);
            case SignalCategory.NATURAL_GROWTH, SignalCategory.CULTIVATION -> this.farmSignals;
            case SignalCategory.PLACED_BLOCK, SignalCategory.INTERACTION -> this.playerBlockSignals;
            case SignalCategory.INFRASTRUCTURE, SignalCategory.BLOCK_ENTITY -> this.machineSignals;
            case SignalCategory.LIGHT_LEAK -> this.lightSignals;
            case SignalCategory.LIVE_ACTIVITY -> this.packetSignals;
            case SignalCategory.ENTITY -> this.entitySignals;
        };
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
        this.chunksPerTick = Math.clamp((long)this.chunksPerTick, 1, 8);
        this.rescanSeconds = Math.clamp((long)this.rescanSeconds, 10, 300);
        this.uiTheme = Math.clamp(this.uiTheme, 0, 2);
        this.performanceProfile = Math.clamp(this.performanceProfile, 0, 2);
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.freecamSpeed = Math.clamp(this.freecamSpeed, 1, 20);
        this.autoEatHunger = Math.clamp(this.autoEatHunger, 1, 20);
        this.lowHealthHearts = Math.clamp(this.lowHealthHearts, 1, 10);
        this.armorAlertPercent = Math.clamp(this.armorAlertPercent, 1, 100);
        this.swingDuration = SwingDuration.clamp(this.swingDuration);
        this.zoomPercent = Math.clamp(this.zoomPercent, 10, 90);
        this.notificationVolume = Math.clamp(this.notificationVolume, 0, 100);
        this.entityEspRange = Math.clamp(this.entityEspRange, 16, 192);
        this.holeEspRange = Math.clamp(this.holeEspRange, 4, 16);
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
