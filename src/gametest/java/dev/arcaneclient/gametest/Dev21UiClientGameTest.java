package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.GuiCategory;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Real overlay input/layout regression in a disposable singleplayer world. */
@SuppressWarnings("UnstableApiUsage")
public final class Dev21UiClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.getInput().resizeWindow(1600, 1000);
            context.runOnClient(client -> {
                var c = ArcaneClient.config();
                c.uiPanelLayout.clear(); c.uiLayoutCustomized = false;
                c.uiFavorites.clear(); c.uiFavorites.add("Player ESP");
                c.uiShowEnabledOnly = c.uiShowFavoritesOnly = false;
                c.devUiRevision = 5; c.uiThemesOpen = true;
                c.uiScalePercent = c.uiDensityPercent = 100;
                c.uiBlur = false; c.uiBackgroundDimPercent = 10;
                c.enabled = c.hud = false;
                client.setScreen(new ArcaneSettingsScreen(null));
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.screen;
                List<GuiCategory> categories = field(screen, "categories");
                Set<String> names = categories.stream().flatMap(c -> c.modules().stream()).map(GuiModule::name).collect(Collectors.toSet());
                require(names.containsAll(Set.of("Sus Chunk Finder", "World Effects", "Entity ESP", "HUD Widgets", "Preview Lab",
                    "Relog", "Elytra Swap", "Copy Coordinates")),
                    "Clean module groups must be registered in the actual menu");
                require(categories.stream().flatMap(c -> c.modules().stream()).anyMatch(m -> m.matches("totem animation")),
                    "Grouped child names must remain searchable");
                require(categories.stream().flatMap(c -> c.modules().stream()).anyMatch(m -> m.matches("custom accessories")),
                    "Grouped visual settings must remain searchable");
                require(categories.stream().allMatch(c -> c.modules().size() <= 12), "No panel should return to an overloaded top-level list");
                require(categories.stream().mapToInt(c -> c.modules().size()).sum() <= 72,
                    "The cleanup must keep the top-level catalog compact");
                require(ArcaneClient.config().uiFavorites.contains("Entity ESP")
                    && !ArcaneClient.config().uiFavorites.contains("Player ESP"), "Legacy favorites must migrate to their group");
                ArcaneClient.config().uiFavorites.clear();
                require(categories.getFirst().name().equals("BASE FINDING"), "Scanner category stays at the top");
                GuiCategory base = categories.getFirst();
                require(base.modules().stream().map(GuiModule::name).collect(Collectors.toSet())
                    .containsAll(Set.of("Base Radar", "Chunk Tiles", "Tunnel ESP", "Amethyst ESP", "Access Trail ESP", "Region Map")),
                    "Discovery modules belong with Base Finding");
                require(!ArcaneClient.config().uiThemesOpen, "Theme editor must not obscure the initial menu");
                require(categories.stream().allMatch(c -> c.y() + c.lastHeight() < screen.height * .65), "Floating panels keep lower world visible");
                GuiModule scanner = find(categories, "Sus Chunk Finder");
                GuiModule effects = find(categories, "World Effects");
                GuiModule entityEsp = find(categories, "Entity ESP");
                require(entityEsp.group() && entityEsp.settings().stream()
                    .anyMatch(setting -> setting instanceof GuiSetting.Toggle toggle
                        && toggle.groupHeader() && toggle.label().equals("Player ESP")),
                    "Grouped children must use compact named toggle rows");
                screen.toggleModuleSettings(scanner);
                screen.toggleModuleSettings(effects);
                require(effects.expanded() && !scanner.expanded(), "Only one detail section is open");
                require(!effects.enabled(), "Opening settings must not enable a feature");
                screen.toggleModuleSettings(effects);
                screen.toggleFavorite("Sus Chunk Finder");
                screen.setModuleFilters(false, true);
                require(screen.visibleModuleCount() == 1, "Favorites hide all unrelated modules");
                EditBox search = field(screen, "searchInput");
                search.setValue("particle");
                require(screen.visibleModuleCount() == 0, "Text search intersects favorites");
                search.setValue("");
                screen.setModuleFilters(false, false);
            });
            context.waitTicks(3);
            context.takeScreenshot("dev23-clean-menu-wide");
            context.runOnClient(client -> ArcaneClient.config().uiFont = 1);
            context.waitTicks(3);
            context.takeScreenshot("dev23-clean-menu-sora");
            context.runOnClient(client -> ArcaneClient.config().uiFont = 0);
            int[] saved = new int[2];
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.screen;
                List<GuiCategory> categories = field(screen, "categories");
                GuiCategory panel = categories.getFirst();
                double sx = (double)client.getWindow().getGuiScaledWidth() / screen.width;
                double sy = (double)client.getWindow().getGuiScaledHeight() / screen.height;
                double x = panel.x() + 14, y = panel.y() + 8;
                screen.mouseClicked(new MouseButtonEvent(x * sx, y * sy, new MouseButtonInfo(0, 0)), false);
                MouseButtonEvent end = new MouseButtonEvent((x + 5) * sx, (y + 22) * sy, new MouseButtonInfo(0, 0));
                screen.mouseDragged(end, 5 * sx, 22 * sy);
                screen.mouseReleased(end);
                saved[0] = panel.x(); saved[1] = panel.y();
                require(ArcaneClient.config().uiLayoutCustomized, "Dragging marks a customized layout");
                screen.close();
                client.setScreen(new ArcaneSettingsScreen(null));
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.screen;
                List<GuiCategory> categories = field(screen, "categories");
                require(categories.getFirst().x() == saved[0] && categories.getFirst().y() == saved[1], "Panel position survives close/reopen");
                screen.resetPanelLayout();
            });
            context.getInput().resizeWindow(960, 640);
            context.waitTicks(6);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.screen;
                List<GuiCategory> categories = field(screen, "categories");
                for (GuiCategory c : categories) require(c.x() >= 0 && c.y() >= 0 && c.y() < screen.height, "Panel header is reachable on small windows");
                require(!screen.shouldPause(), "Overlay must not pause gameplay");
            });
            context.takeScreenshot("dev23-clean-menu-compact");
            context.runOnClient(client -> client.setScreen(null));
        }
    }
    private static GuiModule find(List<GuiCategory> categories, String name) {
        return categories.stream().flatMap(c -> c.modules().stream()).filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object source, String name) {
        try { Field f = source.getClass().getDeclaredField(name); f.setAccessible(true); return (T)f.get(source); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
