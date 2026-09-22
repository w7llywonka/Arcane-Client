package dev.arcaneclient.additions.cosmetics;

import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.GuiSetting;
import java.util.List;

/** Rows appended to Nametags so every field remains independently selectable. */
public final class NametagSettings {
    private NametagSettings() { }

    public static List<GuiSetting> settings(ArcaneConfig config) {
        NametagConfig c = config.nametagAdditions;
        return List.of(
            new GuiSetting.Toggle("Players", () -> c.players, v -> c.players = v),
            new GuiSetting.Toggle("Dropped items", () -> c.items, v -> c.items = v),
            new GuiSetting.Toggle("Self in third person", () -> c.self, v -> c.self = v),
            new GuiSetting.Toggle("Name", () -> c.name, v -> c.name = v),
            new GuiSetting.Toggle("Health", () -> c.health, v -> c.health = v),
            new GuiSetting.Toggle("Distance", () -> c.distance, v -> c.distance = v),
            new GuiSetting.Toggle("Item distance", () -> c.itemDistance, v -> c.itemDistance = v),
            new GuiSetting.Toggle("Item stack count", () -> c.stackCount, v -> c.stackCount = v),
            new GuiSetting.Toggle("Armor icons", () -> c.armor, v -> c.armor = v),
            new GuiSetting.Toggle("Main-hand icon", () -> c.mainHand, v -> c.mainHand = v),
            new GuiSetting.Toggle("Off-hand icon", () -> c.offHand, v -> c.offHand = v),
            new GuiSetting.Slider("Scale", () -> c.scale, v -> c.scale = v, 50, 200, "%"),
            new GuiSetting.Slider("Background opacity", () -> c.backgroundOpacity, v -> c.backgroundOpacity = v, 0, 100, "%")
        );
    }
}
