package dev.arcaneclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.mixin.KeyBindingAccessor;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

@Environment(value=EnvType.CLIENT)
public final class ArcaneKeybinds {
    private final KeyMapping scanner;
    private final KeyMapping overlay;
    private final KeyMapping freecam;
    private final KeyMapping freelook;
    private final KeyMapping zoom;
    private final KeyMapping cleanCapture;
    private final KeyMapping esp;
    private final KeyMapping autoTotem;
    private final KeyMapping tunnelEsp;
    private final KeyMapping itemEsp;
    private final KeyMapping settings;
    private final KeyMapping relog;
    private final KeyMapping autoWalk;
    private final KeyMapping autoSneak;
    private final KeyMapping coordinateClipboard;
    private final KeyMapping waypoint;
    private final List<KeyMapping> chatMacros;
    private boolean legacyBindingsChecked;

    public ArcaneKeybinds() {
        KeyMapping.Category category = KeyMapping.Category.register((Identifier)ArcaneClient.id("main"));
        this.scanner = ArcaneKeybinds.register("key.arcaneclient.toggle", InputConstants.KEY_B, category);
        this.overlay = ArcaneKeybinds.register("key.arcaneclient.overlay", InputConstants.KEY_N, category);
        this.freecam = ArcaneKeybinds.register("key.arcaneclient.freecam", InputConstants.KEY_F6, category);
        this.freelook = ArcaneKeybinds.register("key.arcaneclient.freelook", InputConstants.UNKNOWN.getValue(), category);
        this.zoom = ArcaneKeybinds.register("key.arcaneclient.zoom", InputConstants.KEY_C, category);
        this.cleanCapture = ArcaneKeybinds.register("key.arcaneclient.clean_capture", InputConstants.UNKNOWN.getValue(), category);
        this.esp = ArcaneKeybinds.register("key.arcaneclient.esp", InputConstants.KEY_F7, category);
        this.autoTotem = ArcaneKeybinds.register("key.arcaneclient.auto_totem", InputConstants.KEY_F8, category);
        this.tunnelEsp = ArcaneKeybinds.register("key.arcaneclient.tunnel_esp", InputConstants.KEY_F9, category);
        this.itemEsp = ArcaneKeybinds.register("key.arcaneclient.item_esp", InputConstants.KEY_F10, category);
        this.settings = ArcaneKeybinds.register("key.arcaneclient.settings", InputConstants.KEY_RSHIFT, category);
        this.relog = ArcaneKeybinds.register("key.arcaneclient.relog", -1, category);
        this.autoWalk = ArcaneKeybinds.register("key.arcaneclient.auto_walk", -1, category);
        this.autoSneak = ArcaneKeybinds.register("key.arcaneclient.auto_sneak", -1, category);
        this.coordinateClipboard = ArcaneKeybinds.register("key.arcaneclient.coordinate_clipboard", -1, category);
        this.waypoint = ArcaneKeybinds.register("key.arcaneclient.waypoint", -1, category);
        this.chatMacros = List.of(ArcaneKeybinds.register("key.arcaneclient.chat_macro_1", -1, category), ArcaneKeybinds.register("key.arcaneclient.chat_macro_2", -1, category), ArcaneKeybinds.register("key.arcaneclient.chat_macro_3", -1, category), ArcaneKeybinds.register("key.arcaneclient.chat_macro_4", -1, category));
    }

    public KeyMapping scanner() {
        return this.scanner;
    }

    public KeyMapping overlay() {
        return this.overlay;
    }

    public KeyMapping freecam() {
        return this.freecam;
    }

    public KeyMapping freelook() {
        return this.freelook;
    }

    public KeyMapping zoom() {
        return this.zoom;
    }

    public KeyMapping cleanCapture() {
        return this.cleanCapture;
    }

    public KeyMapping esp() {
        return this.esp;
    }

    public KeyMapping autoTotem() {
        return this.autoTotem;
    }

    public KeyMapping tunnelEsp() {
        return this.tunnelEsp;
    }

    public KeyMapping itemEsp() {
        return this.itemEsp;
    }

    public KeyMapping settings() {
        return this.settings;
    }

    public KeyMapping relog() {
        return this.relog;
    }

    public KeyMapping autoWalk() {
        return this.autoWalk;
    }

    public KeyMapping autoSneak() {
        return this.autoSneak;
    }

    public KeyMapping coordinateClipboard() {
        return this.coordinateClipboard;
    }

    public KeyMapping waypoint() {
        return this.waypoint;
    }

    public List<KeyMapping> chatMacros() {
        return this.chatMacros;
    }

    /**
     * Minecraft 26.3 replaced GLFW key values with SDL scan codes. Early 26.3
     * Arcane builds saved the old numeric defaults into options.txt. Migrate
     * those exact per-action defaults once, after Minecraft has loaded options.
     */
    public boolean migrateLegacyBindings() {
        if (this.legacyBindingsChecked) return false;
        this.legacyBindingsChecked = true;

        boolean changed = false;
        changed |= migrate(this.scanner, 66, InputConstants.KEY_B);
        changed |= migrate(this.overlay, 78, InputConstants.KEY_N);
        changed |= migrate(this.freecam, 295, InputConstants.KEY_F6);
        changed |= migrate(this.zoom, 67, InputConstants.KEY_C);
        changed |= migrate(this.esp, 296, InputConstants.KEY_F7);
        changed |= migrate(this.autoTotem, 297, InputConstants.KEY_F8);
        changed |= migrate(this.tunnelEsp, 298, InputConstants.KEY_F9);
        changed |= migrate(this.itemEsp, 299, InputConstants.KEY_F10);
        changed |= migrate(this.settings, 344, InputConstants.KEY_RSHIFT);
        if (changed) KeyMapping.resetMapping();
        return changed;
    }

    private static boolean migrate(KeyMapping mapping, int legacyValue, int sdlValue) {
        InputConstants.Key key = ((KeyBindingAccessor)(Object)mapping).arcaneclient$boundKey();
        if (key.getType() != InputConstants.Type.KEYBOARD || key.getValue() != legacyValue) return false;
        mapping.setKey(InputConstants.Type.KEYBOARD.getOrCreate(sdlValue));
        return true;
    }

    private static KeyMapping register(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping(name, key, category));
    }
}
