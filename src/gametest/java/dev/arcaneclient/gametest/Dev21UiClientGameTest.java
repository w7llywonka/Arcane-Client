package dev.arcaneclient.gametest;

import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ArcaneWelcomeScreen;
import dev.arcaneclient.screen.BlockEspPickerScreen;
import dev.arcaneclient.screen.ClickGuiTheme;
import dev.arcaneclient.screen.ConfigLibraryScreen;
import dev.arcaneclient.screen.GuiCategory;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import dev.arcaneclient.screen.ItemEspPickerScreen;
import dev.arcaneclient.screen.TextSettingsScreen;
import dev.arcaneclient.screen.ThemePanel;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Real overlay input/layout regression in a disposable singleplayer world. */
@SuppressWarnings("UnstableApiUsage")
public final class Dev21UiClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null
                && client.levelRenderer.hasRenderedAllSections(), 5000);
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
                client.gui.setScreen(new ArcaneSettingsScreen(null));
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                Set<String> names = categories.stream().flatMap(c -> c.modules().stream()).map(GuiModule::name).collect(Collectors.toSet());
                require(names.containsAll(Set.of("Sus Chunk Finder", "World Effects", "Entity ESP", "HUD Widgets", "Preview Lab",
                    "Relog", "Elytra Swap", "Copy Coordinates")),
                    "Clean module groups must be registered in the actual menu");
                require(categories.stream().flatMap(c -> c.modules().stream()).anyMatch(m -> m.matches("totem animation")),
                    "Grouped child names must remain searchable");
                require(categories.stream().flatMap(c -> c.modules().stream()).anyMatch(m -> m.matches("custom accessories")),
                    "Grouped visual settings must remain searchable");
                require(categories.stream().allMatch(c -> c.modules().size() <= 14),
                    "No panel should return to an overloaded top-level list: "
                        + categories.stream().map(c -> c.name() + "=" + c.modules().size()).toList());
                require(categories.stream().mapToInt(c -> c.modules().size()).sum() <= 80,
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
            verifyPolishedControls(context);
            int[] saved = new int[2];
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                GuiCategory panel = categories.getFirst();
                double sx = (double)client.getWindow().getGuiScaledWidth() / screen.width;
                double sy = (double)client.getWindow().getGuiScaledHeight() / screen.height;
                double x = panel.x() + 14, y = panel.y() + 8;
                screen.mouseClicked(new MouseButtonEvent(x * sx, y * sy, new MouseButtonInfo(SDL_BUTTON_LEFT, 0)), false);
                MouseButtonEvent end = new MouseButtonEvent((x + 5) * sx, (y + 22) * sy, new MouseButtonInfo(SDL_BUTTON_LEFT, 0));
                screen.mouseDragged(end, 5 * sx, 22 * sy);
                screen.mouseReleased(end);
                saved[0] = panel.x(); saved[1] = panel.y();
                require(ArcaneClient.config().uiLayoutCustomized, "Dragging marks a customized layout");
                screen.onClose();
                client.gui.setScreen(new ArcaneSettingsScreen(null));
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                require(categories.getFirst().x() == saved[0] && categories.getFirst().y() == saved[1], "Panel position survives close/reopen");
                screen.resetPanelLayout();
            });
            context.getInput().resizeWindow(960, 640);
            context.waitTicks(6);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                for (GuiCategory c : categories) require(c.x() >= 0 && c.y() >= 0 && c.y() < screen.height, "Panel header is reachable on small windows");
                require(!screen.isPauseScreen(), "Overlay must not pause gameplay");
            });
            context.takeScreenshot("dev23-clean-menu-compact");
            verifySecondaryScreens(context);
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }

    private static void verifySecondaryScreens(ClientGameTestContext context) {
        Screen[] parent = new Screen[1];
        context.getInput().resizeWindow(1600, 1000);
        context.waitTicks(4);
        context.runOnClient(client -> {
            parent[0] = client.gui.screen();
            client.gui.setScreen(new ArcaneWelcomeScreen(parent[0]));
            var welcome = (ArcaneWelcomeScreen)client.gui.screen();
            // Focus Continue directly so this check can never activate the external website button.
            AbstractWidget proceed = widget(welcome, "Continue");
            welcome.setFocused(proceed);
            proceed.setFocused(true);
        });
        context.waitTicks(3);
        context.takeScreenshot("polished-ui-welcome");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(2);
        context.runOnClient(client -> {
            require(client.gui.screen() == parent[0], "Keyboard Continue must return to the welcome parent");
            client.gui.setScreen(new ArcaneWelcomeScreen(parent[0]));
            clickWidget(client.gui.screen(), "Continue");
            require(client.gui.screen() == parent[0], "Click Continue must return to the welcome parent");
            client.gui.setScreen(new ConfigLibraryScreen(parent[0], ArcaneClient.config()));
        });
        context.waitTicks(3);
        context.takeScreenshot("polished-ui-config-library");
        context.runOnClient(client -> {
            client.gui.screen().onClose();
            require(client.gui.screen() == parent[0], "Closing the profile library must return to the menu");
            client.gui.setScreen(new ItemEspPickerScreen(parent[0], ArcaneClient.config()));
            EditBox search = field(client.gui.screen(), "search");
            search.setValue("minecraft:diamond");
            List<?> matches = field(client.gui.screen(), "filtered");
            require(!matches.isEmpty(), "Registry ID search must find diamond items");
        });
        context.waitTicks(3);
        context.takeScreenshot("polished-ui-item-picker");
        context.runOnClient(client -> {
            client.gui.setScreen(new BlockEspPickerScreen(parent[0], ArcaneClient.config()));
            EditBox search = field(client.gui.screen(), "search");
            search.setValue("minecraft:diamond");
            List<?> matches = field(client.gui.screen(), "filtered");
            require(!matches.isEmpty(), "Registry ID search must find diamond blocks");
        });
        context.waitTicks(3);
        context.takeScreenshot("polished-ui-block-picker");
        String[] draft = {"Example label"};
        context.runOnClient(client -> {
            client.gui.setScreen(new TextSettingsScreen(parent[0], ArcaneClient.config(), "Text settings",
                "Changes are saved when you choose Save.", List.of(new TextSettingsScreen.Field(
                    "Display label", "A short label for this example.", () -> draft[0], value -> draft[0] = value, 48))));
            List<EditBox> inputs = field(client.gui.screen(), "inputs");
            inputs.getFirst().setValue("Edited draft");
            require(draft[0].equals("Example label"), "Editing a form must keep its value as a draft");
        });
        context.waitTicks(3);
        context.takeScreenshot("polished-ui-text-form");
        context.runOnClient(client -> {
            clickWidget(client.gui.screen(), "Cancel");
            require(client.gui.screen() == parent[0] && draft[0].equals("Example label"),
                "Cancel must return to the parent without applying text changes");
        });
    }

    private static AbstractWidget widget(Screen screen, String label) {
        return screen.children().stream().filter(child -> child instanceof AbstractWidget)
            .map(child -> (AbstractWidget)child).filter(child -> child.getMessage().getString().equals(label))
            .findFirst().orElseThrow(() -> new AssertionError("Missing screen button: " + label));
    }

    private static void clickWidget(Screen screen, String label) {
        AbstractWidget button = widget(screen, label);
        MouseButtonEvent click = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
            button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(SDL_BUTTON_LEFT, 0));
        screen.mouseClicked(click, false);
        screen.mouseReleased(click);
    }

    /** Uses the live row geometry so visual spacing changes cannot invalidate the input checks. */
    private static void verifyPolishedControls(ClientGameTestContext context) {
        UiSnapshot[] original = new UiSnapshot[1];
        context.runOnClient(client -> original[0] = UiSnapshot.capture(ArcaneClient.config()));
        try {
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                GuiModule freecam = find(categories, "Freecam");
                EditBox search = field(screen, "searchInput");
                search.setValue("freecam");
                screen.toggleModuleSettings(freecam);
                require(freecam.expanded() && !freecam.enabled(), "Opening camera settings must keep the camera disabled");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                GuiModule freecam = find(categories, "Freecam");
                GuiSetting.Slider speed = (GuiSetting.Slider)freecam.settings().stream()
                    .filter(s -> s.label().equals("Speed")).findFirst().orElseThrow();
                SettingBounds row = settingBounds(screen, freecam, speed);
                int trackX = call(screen, "sliderTrackX", new Class<?>[] {int.class}, row.x());
                int trackWidth = call(screen, "sliderTrackWidth", new Class<?>[0]);
                int trackY = row.y() + row.height() - 9;
                MouseButtonEvent down = clickAt(screen, trackX + trackWidth / 2.0, trackY);
                screen.mouseClicked(down, false);
                screen.mouseDragged(clickAt(screen, trackX - 30, trackY), 0, 0);
                require(speed.fraction() == 0.0f, "Dragging beyond the slider start must clamp to its minimum");
                MouseButtonEvent end = clickAt(screen, trackX + trackWidth + 30, trackY);
                screen.mouseDragged(end, 0, 0);
                require(speed.fraction() == 1.0f, "Dragging beyond the slider end must clamp to its maximum");
                screen.mouseReleased(end);
                require(field(screen, "draggingSlider") == null, "Releasing a slider must end its drag");
                ArcaneClient.config().freecamSpeed = original[0].freecamSpeed();

                GuiSetting.Toggle mining = (GuiSetting.Toggle)freecam.settings().stream()
                    .filter(s -> s.label().equals("Directional mining")).findFirst().orElseThrow();
                SettingBounds toggleRow = settingBounds(screen, freecam, mining);
                MouseButtonEvent toggle = clickAt(screen, toggleRow.x() + toggleRow.width() / 2.0,
                    toggleRow.y() + toggleRow.height() / 2.0);
                boolean before = mining.value();
                screen.mouseClicked(toggle, false);
                require(mining.value() != before, "Clicking a nested toggle must update its setting");
                screen.mouseClicked(toggle, false);
                require(mining.value() == before && !freecam.enabled(), "Nested settings must preserve the module's enabled state");
            });
            context.waitTicks(3);
            context.takeScreenshot("polished-ui-settings");
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                GuiModule interfaceModule = find(categories, "Interface");
                EditBox search = field(screen, "searchInput");
                search.setValue("interface");
                screen.toggleModuleSettings(interfaceModule);
                GuiSetting.Cycle theme = (GuiSetting.Cycle)interfaceModule.settings().stream()
                    .filter(s -> s.label().equals("Theme")).findFirst().orElseThrow();
                SettingBounds row = settingBounds(screen, interfaceModule, theme);
                int before = ArcaneClient.config().uiTheme;
                screen.mouseClicked(clickAt(screen, row.x() + row.width() / 2.0, row.y() + row.height() / 2.0), false);
                require(ArcaneClient.config().uiTheme == (before + 1) % ClickGuiTheme.count(),
                    "Clicking a cycle row must advance its value exactly once");
                ArcaneClient.config().uiTheme = original[0].theme();
                screen.toggleModuleSettings(interfaceModule);
                search.setValue("");
                screen.mouseClicked(clickAt(screen, screen.width - 27, screen.height - 10), false);
                ThemePanel panel = field(screen, "themePanel");
                require(panel.isOpen() && ArcaneClient.config().uiThemesOpen, "The theme button must open the editor");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                ThemePanel panel = field(screen, "themePanel");
                int panelWidth = field(panel, "WIDTH"), header = field(panel, "HEADER"), rowHeight = field(panel, "ROW");
                screen.mouseClicked(clickAt(screen, panel.x() + panelWidth / 2.0,
                    panel.y() + header + rowHeight + rowHeight / 2.0), false);
                require(ArcaneClient.config().uiTheme == 1 && !ArcaneClient.config().customUiColors,
                    "Choosing a preset must apply it through the editor");
            });
            context.waitTicks(3);
            context.takeScreenshot("polished-ui-themes");
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                ThemePanel panel = field(screen, "themePanel");
                int panelWidth = field(panel, "WIDTH"), header = field(panel, "HEADER");
                screen.mouseClicked(clickAt(screen, panel.x() + panelWidth - 5, panel.y() + header / 2.0), false);
                require(!panel.isOpen() && !ArcaneClient.config().uiThemesOpen, "The editor close button must dismiss it");
                original[0].restore(ArcaneClient.config());
                ArcaneClient.config().uiVectorRendering = false;
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                require(!Boolean.TRUE.equals(field(screen, "lastVectorFrame")), "Disabling smooth rendering must use the native fallback");
                require(screen.visibleModuleCount() > 0 && !screen.isPauseScreen(), "Fallback must retain the interactive world overlay");
            });
            context.takeScreenshot("polished-ui-native-fallback");
            context.runOnClient(client -> {
                var c = ArcaneClient.config();
                c.uiVectorRendering = original[0].vector();
                c.uiReducedMotion = true;
                c.fullbright = !original[0].fullbright();
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var screen = (ArcaneSettingsScreen)client.gui.screen();
                require(Float.valueOf(1.0f).equals(field(screen, "animationStep")),
                    "Reduced motion must resolve state changes without an animation delay");
            });
            context.takeScreenshot("polished-ui-reduced-motion");
        } finally {
            context.runOnClient(client -> {
                original[0].restore(ArcaneClient.config());
                if (client.gui.screen() instanceof ArcaneSettingsScreen screen) {
                    ThemePanel panel = field(screen, "themePanel");
                    panel.setOpen(false);
                    List<GuiCategory> categories = field(screen, "categories");
                    categories.stream().flatMap(c -> c.modules().stream()).forEach(m -> m.setExpanded(false));
                    EditBox search = field(screen, "searchInput");
                    search.setValue("");
                    screen.setModuleFilters(false, false);
                }
            });
        }
    }

    private static MouseButtonEvent clickAt(ArcaneSettingsScreen screen, double x, double y) {
        var window = Minecraft.getInstance().getWindow();
        double sx = (double)window.getGuiScaledWidth() / screen.width;
        double sy = (double)window.getGuiScaledHeight() / screen.height;
        return new MouseButtonEvent(x * sx, y * sy, new MouseButtonInfo(SDL_BUTTON_LEFT, 0));
    }

    private static SettingBounds settingBounds(ArcaneSettingsScreen screen, GuiModule module, GuiSetting setting) {
        List<GuiCategory> categories = field(screen, "categories");
        GuiCategory category = categories.stream().filter(c -> c.modules().contains(module)).findFirst().orElseThrow();
        List<?> rows = call(screen, "layout", new Class<?>[] {GuiCategory.class}, category);
        Object row = rows.stream().filter(r -> field(r, "setting") == setting).findFirst().orElseThrow();
        int x = field(row, "x"), y = field(row, "y"), width = field(row, "width"), height = field(row, "height");
        require(y >= category.y() && y + height <= category.y() + category.lastHeight(), "Control must be fully visible before clicking it");
        return new SettingBounds(x, y, width, height);
    }

    private record SettingBounds(int x, int y, int width, int height) {}

    private record UiSnapshot(int theme, int opacity, int freecamSpeed, boolean customColors,
                              boolean vector, boolean reducedMotion, boolean fullbright, boolean freecamMining) {
        private static UiSnapshot capture(ArcaneConfig c) {
            return new UiSnapshot(c.uiTheme, c.uiOpacityPercent, c.freecamSpeed, c.customUiColors,
                c.uiVectorRendering, c.uiReducedMotion, c.fullbright, c.freecamMining);
        }
        private void restore(ArcaneConfig c) {
            c.uiTheme = theme; c.uiOpacityPercent = opacity; c.freecamSpeed = freecamSpeed;
            c.customUiColors = customColors; c.uiVectorRendering = vector; c.uiReducedMotion = reducedMotion;
            c.fullbright = fullbright; c.freecamMining = freecamMining;
        }
    }

    @SuppressWarnings("unchecked") private static <T> T call(Object object, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            var method = object.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return (T)method.invoke(object, arguments);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
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
