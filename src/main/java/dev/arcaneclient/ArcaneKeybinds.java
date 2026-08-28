package dev.arcaneclient;

import dev.arcaneclient.ArcaneClient;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

@Environment(value=EnvType.CLIENT)
public final class ArcaneKeybinds {
    private final KeyBinding scanner;
    private final KeyBinding overlay;
    private final KeyBinding freecam;
    private final KeyBinding freelook;
    private final KeyBinding zoom;
    private final KeyBinding cleanCapture;
    private final KeyBinding esp;
    private final KeyBinding autoTotem;
    private final KeyBinding tunnelEsp;
    private final KeyBinding itemEsp;
    private final KeyBinding settings;
    private final List<KeyBinding> chatMacros;

    public ArcaneKeybinds() {
        KeyBinding.Category category = KeyBinding.Category.create((Identifier)ArcaneClient.id("main"));
        this.scanner = ArcaneKeybinds.register("key.arcaneclient.toggle", 66, category);
        this.overlay = ArcaneKeybinds.register("key.arcaneclient.overlay", 78, category);
        this.freecam = ArcaneKeybinds.register("key.arcaneclient.freecam", 295, category);
        this.freelook = ArcaneKeybinds.register("key.arcaneclient.freelook", -1, category);
        this.zoom = ArcaneKeybinds.register("key.arcaneclient.zoom", 67, category);
        this.cleanCapture = ArcaneKeybinds.register("key.arcaneclient.clean_capture", -1, category);
        this.esp = ArcaneKeybinds.register("key.arcaneclient.esp", 296, category);
        this.autoTotem = ArcaneKeybinds.register("key.arcaneclient.auto_totem", 297, category);
        this.tunnelEsp = ArcaneKeybinds.register("key.arcaneclient.tunnel_esp", 298, category);
        this.itemEsp = ArcaneKeybinds.register("key.arcaneclient.item_esp", 299, category);
        this.settings = ArcaneKeybinds.register("key.arcaneclient.settings", 344, category);
        this.chatMacros = List.of(ArcaneKeybinds.register("key.arcaneclient.chat_macro_1", -1, category), ArcaneKeybinds.register("key.arcaneclient.chat_macro_2", -1, category), ArcaneKeybinds.register("key.arcaneclient.chat_macro_3", -1, category), ArcaneKeybinds.register("key.arcaneclient.chat_macro_4", -1, category));
    }

    public KeyBinding scanner() {
        return this.scanner;
    }

    public KeyBinding overlay() {
        return this.overlay;
    }

    public KeyBinding freecam() {
        return this.freecam;
    }

    public KeyBinding freelook() {
        return this.freelook;
    }

    public KeyBinding zoom() {
        return this.zoom;
    }

    public KeyBinding cleanCapture() {
        return this.cleanCapture;
    }

    public KeyBinding esp() {
        return this.esp;
    }

    public KeyBinding autoTotem() {
        return this.autoTotem;
    }

    public KeyBinding tunnelEsp() {
        return this.tunnelEsp;
    }

    public KeyBinding itemEsp() {
        return this.itemEsp;
    }

    public KeyBinding settings() {
        return this.settings;
    }

    public List<KeyBinding> chatMacros() {
        return this.chatMacros;
    }

    private static KeyBinding register(String name, int key, KeyBinding.Category category) {
        return KeyBindingHelper.registerKeyBinding((KeyBinding)new KeyBinding(name, key, category));
    }
}
