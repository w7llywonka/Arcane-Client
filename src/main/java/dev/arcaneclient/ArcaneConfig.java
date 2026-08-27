package dev.arcaneclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.chat.ChatMacroMessage;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.model.SignalCategory;
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

            config.clamp();
            if (upgradedSpeedDefaults) {
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

    private void clamp() {
        this.threshold = Math.clamp((long)this.threshold, 0, 100);
        this.scanRadius = Math.clamp((long)this.scanRadius, 2, 24);
        this.chunksPerTick = Math.clamp((long)this.chunksPerTick, 1, 8);
        this.rescanSeconds = Math.clamp((long)this.rescanSeconds, 10, 300);
        this.uiTheme = Math.clamp(this.uiTheme, 0, 2);
        this.chatMacro1 = ChatMacroMessage.normalize(this.chatMacro1);
        this.chatMacro2 = ChatMacroMessage.normalize(this.chatMacro2);
        this.chatMacro3 = ChatMacroMessage.normalize(this.chatMacro3);
        this.chatMacro4 = ChatMacroMessage.normalize(this.chatMacro4);
    }
}
