package dev.arcaneclient.gametest;

import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_RIGHT;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.render.TraceRenderer;
import dev.arcaneclient.screen.*;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.level.ChunkPos;

/** Isolated UI smoke test; does not connect to a server or touch a user's game instance. */
public final class DevUiClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
        context.waitFor(client -> client.level != null && client.player != null
            && client.levelRenderer.hasRenderedAllSections(), 5000);
        context.getInput().resizeWindow(1600, 1000);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            ArcaneClient.config().uiTheme = 0;
            ArcaneClient.config().uiPanelLayout.clear();
            ArcaneClient.config().uiLayoutCustomized = false;
            ArcaneClient.config().uiFavorites.clear();
            ArcaneClient.config().uiShowEnabledOnly = false;
            ArcaneClient.config().uiShowFavoritesOnly = false;
            ArcaneClient.config().uiReducedMotion = false;
            ArcaneClient.config().customUiColors = false;
            ArcaneClient.config().devUiRevision = 1;
            ArcaneClient.config().uiBackgroundDimPercent = 42;
            ArcaneClient.config().uiCornerRadius = 12;
            client.gui.setScreen(new ArcaneSettingsScreen(null));
        });
        context.waitTicks(8);
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.gui.screen();
            List<GuiCategory> categories = field(screen, "categories");
            require(categories.getFirst().name().equals("BASE FINDING"), "Base Finding must be first");
            require(categories.stream().allMatch(GuiCategory::open), "All categories start open");
            require(categories.stream().map(GuiCategory::y).distinct().count() == 1, "Desktop categories must share one top row");
            require(categories.stream().allMatch(c -> c.y() + c.lastHeight() < screen.height * 0.65), "Desktop panels leave the lower world visible");
            require(ArcaneClient.config().uiOpacityPercent == 90, "Older UI settings migrate to readable panels");
            require(ArcaneClient.config().uiBackgroundDimPercent == 18, "World remains visible behind the overlay");
            require(ArcaneClient.config().uiCornerRadius == 6, "Panels retain rounded corners");
            require(ArcaneClient.config().devUiRevision == 6, "Cleanup migration must be recorded once");
            require(!ArcaneClient.config().uiThemesOpen && ArcaneClient.config().uiSingleSettings,
                "Theme editor starts closed and single-section details prevent clutter");
            require(client.level != null, "Overlay test must run over an actual world");
            require(!screen.isPauseScreen(), "Overlay must leave the world running");
            GuiCategory first = categories.getFirst();
            UiGeometry.ClickGuiMetrics metrics = field(screen, "metrics");
            GuiModule module = first.visible().getFirst();
            boolean original = module.enabled();
            int x = first.x() + 14;
            int y = first.y() + metrics.headerHeight() + metrics.moduleHeight() / 2;
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(SDL_BUTTON_LEFT, 0)), false);
            require(module.enabled() != original, "Left click must toggle");
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(SDL_BUTTON_LEFT, 0)), false);
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(SDL_BUTTON_RIGHT, 0)), false);
            require(module.expanded(), "Right click must expand settings");
            GuiModule other = categories.stream().flatMap(c -> c.modules().stream())
                .filter(m -> m != module && m.hasSettings()).findFirst().orElseThrow();
            boolean otherEnabled = other.enabled();
            screen.toggleModuleSettings(other);
            require(!module.expanded() && other.expanded(), "Opening details closes the previous section");
            require(other.enabled() == otherEnabled, "Opening details does not toggle the feature");
            screen.toggleModuleSettings(module);
            require(module.expanded() && !other.expanded(), "Only the selected details remain open");
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(SDL_BUTTON_RIGHT, 0)), false);
            screen.mouseClicked(new MouseButtonEvent(first.x() + metrics.windowWidth() - 13, y, new MouseButtonInfo(SDL_BUTTON_LEFT, 0)), false);
            require(!module.expanded() && module.enabled() != original, "Clicking the visible switch must toggle, not open settings");
            screen.mouseClicked(new MouseButtonEvent(first.x() + metrics.windowWidth() - 13, y, new MouseButtonInfo(SDL_BUTTON_LEFT, 0)), false);
            EditBox search = field(screen, "searchInput");
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(SDL_BUTTON_LEFT, InputConstants.MOD_SHIFT)), false);
            require(ArcaneClient.config().uiFavorites.contains(module.name()), "Shift-click must favorite a module");
            require(module.enabled() == original, "Favoriting must not toggle the underlying feature");
            screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(SDL_BUTTON_LEFT, InputConstants.MOD_SHIFT)), false);
            require(!ArcaneClient.config().uiFavorites.contains(module.name()), "A second shift-click must remove the favorite");
            require(module.enabled() == original, "Removing a favorite must not toggle the underlying feature");
            search.setValue("freecam");
            require(categories.stream().flatMap(c -> c.visible().stream()).anyMatch(m -> m.name().equals("Freecam")), "Search must find Freecam");
            search.setValue("");
        });
        context.waitTicks(5);
        context.takeScreenshot("dev-ui-wide");
        boolean[] originalFullbright = new boolean[1];
        int[] originalModuleCount = new int[1];
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.gui.screen();
            List<GuiCategory> categories = field(screen, "categories");
            originalModuleCount[0] = screen.visibleModuleCount();
            originalFullbright[0] = ArcaneClient.config().fullbright;
            ArcaneClient.config().fullbright = true;
            screen.toggleFavorite("Fullbright");
            require(ArcaneClient.config().fullbright, "Favorite helper must not change enabled state");
            screen.setModuleFilters(false, true);
            require(screen.visibleModuleCount() == 1, "Favorites filter must show only the chosen favorite");
            require(visibleModules(categories).getFirst().name().equals("Fullbright"), "Favorites filter selected the wrong module");
            screen.setModuleFilters(true, true);
            require(screen.visibleModuleCount() == 1, "An enabled favorite must survive combined filters");
        });
        context.waitTicks(4);
        context.takeScreenshot("dev-ui-favorites");
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.gui.screen();
            List<GuiCategory> categories = field(screen, "categories");
            EditBox search = field(screen, "searchInput");
            search.setValue("freecam");
            require(screen.visibleModuleCount() == 0, "Search must intersect with favorites, not bypass them");
            search.setValue("");
            ArcaneClient.config().fullbright = false;
            screen.setModuleFilters(true, true);
            require(screen.visibleModuleCount() == 0, "Disabled favorites must disappear from the enabled-only view");
            screen.setModuleFilters(true, false);
            require(visibleModules(categories).stream().allMatch(GuiModule::enabled), "Enabled filter must hide inactive modules");
            screen.setModuleFilters(false, false);
            require(screen.visibleModuleCount() == originalModuleCount[0], "Clearing filters must restore every module");
            ArcaneClient.config().fullbright = originalFullbright[0];
        });
        context.runOnClient(client -> {
            List<GuiCategory> categories = field(client.gui.screen(), "categories");
            for (GuiCategory category : categories) category.scrollBy(category.maxScroll());
        });
        context.waitTicks(3);
        context.takeScreenshot("dev-ui-rounded-scrolled-bottom");
        context.getInput().resizeWindow(960, 640);
        context.waitTicks(5);
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.gui.screen();
            List<GuiCategory> categories = field(screen, "categories");
            require(categories.getFirst().y() < screen.height / 2, "Base Finding must stay near the top after resize");
            require(categories.stream().allMatch(c -> c.x() >= 0 && c.y() >= 0 && c.y() < screen.height), "Headers remain reachable after resize");
            UiGeometry.ClickGuiMetrics metrics = field(screen, "metrics");
            for (int i = 0; i < categories.size(); i++) for (int j = i + 1; j < categories.size(); j++) {
                GuiCategory a = categories.get(i), b = categories.get(j);
                boolean overlap = a.x() < b.x() + metrics.windowWidth() && b.x() < a.x() + metrics.windowWidth()
                    && a.y() < b.y() + b.lastHeight() && b.y() < a.y() + a.lastHeight();
                require(!overlap, "Auto-layout panels must not overlap after resize");
            }
        });
        context.takeScreenshot("dev-ui-compact");
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.gui.screen();
            List<GuiCategory> categories = field(screen, "categories");
            GuiCategory moved = categories.getFirst();
            int beforeX = moved.x(), beforeY = moved.y();
            MouseButtonEvent down = new MouseButtonEvent(beforeX + 12, beforeY + 6, new MouseButtonInfo(SDL_BUTTON_LEFT, 0));
            screen.mouseClicked(down, false);
            MouseButtonEvent end = new MouseButtonEvent(beforeX + 20, beforeY + 23, new MouseButtonInfo(SDL_BUTTON_LEFT, 0));
            screen.mouseDragged(end, 8, 17);
            screen.mouseReleased(end);
            int x = moved.x(), y = moved.y();
            int scroll = moved.scrollOffset();
            require(x != beforeX || y != beforeY, "Test must drag a real panel");
            EditBox search = field(screen, "searchInput");
            screen.setModuleFilters(false, true);
            search.setValue("fullbright");
            client.resizeGui();
            screen.onClose();
            var disk = dev.arcaneclient.ArcaneConfig.load();
            require(disk.uiLayoutCustomized, "Customized layout must be written to disk");
            var saved = disk.uiPanelLayout.get(moved.name());
            require(saved.x() == x && saved.y() == y, "Search and favorites must not overwrite saved position");
            require(saved.scroll() == scroll, "Filters must not overwrite the saved panel scroll");
            require(disk.uiShowFavoritesOnly && !disk.uiShowEnabledOnly, "Filter selection must be saved on close");
            require(disk.uiFavorites.contains("Fullbright"), "Favorite modules must be saved on close");
            client.gui.setScreen(new ArcaneSettingsScreen(null));
            ArcaneSettingsScreen reopenedScreen = (ArcaneSettingsScreen) client.gui.screen();
            require(reopenedScreen.visibleModuleCount() == 1, "Favorite filter must be restored on reopen");
            reopenedScreen.setModuleFilters(false, false);
            List<GuiCategory> reopened = field(reopenedScreen, "categories");
            GuiCategory restored = reopened.stream().filter(category -> category.name().equals(moved.name())).findFirst().orElseThrow();
            require(restored.x() == x && restored.y() == y, "Closing/reopening must restore dragged position");
            require(restored.scrollOffset() == scroll, "Clearing reopened filters must restore panel scrolling");
            require(restored.open(), "Expanded state must survive reopening");
            reopenedScreen.setModuleFilters(false, true);
            EditBox reopenedSearch = field(reopenedScreen, "searchInput");
            reopenedSearch.setValue("fullbright");
            reopenedScreen.resetPanelLayout();
            require(!ArcaneClient.config().uiLayoutCustomized, "Reset must clear the customized layout flag");
            require(!ArcaneClient.config().uiShowEnabledOnly && !ArcaneClient.config().uiShowFavoritesOnly,
                "Reset must clear active module filters");
            require(reopenedScreen.visibleModuleCount() == originalModuleCount[0], "Reset must restore all module rows");
            int resetX = restored.x(), resetY = restored.y();
            require(resetX != x || resetY != y, "Reset must replace the dragged position with the automatic layout");
            reopenedScreen.setModuleFilters(false, false);
            require(restored.x() == resetX && restored.y() == resetY, "Clearing filters after reset must not revive the old custom position");
            reopenedScreen.onClose();
            var resetDisk = dev.arcaneclient.ArcaneConfig.load();
            var resetSaved = resetDisk.uiPanelLayout.get(moved.name());
            require(resetSaved.x() == resetX && resetSaved.y() == resetY, "Reset layout must be saved instead of the pre-filter layout");
        });
        context.runOnClient(client -> client.gui.setScreen(null));
        context.runOnClient(client -> {
            ArcaneClient.config().nametags = true;
            ArcaneClient.config().nametagRange = 16;
            ArcaneClient.config().itemEsp = false;
            client.player.setYRot(0); client.player.setXRot(0);
        });
        singleplayer.getServer().runCommand("execute at ArcaneQA run summon minecraft:item ~ ~1 ~4 {Tags:[\"arcane_name_qa\"],NoGravity:1b,PickupDelay:32767s,Item:{id:\"minecraft:diamond\",count:3}}");
        singleplayer.getServer().runCommand("execute at ArcaneQA run summon minecraft:item ~ ~1 ~40 {Tags:[\"arcane_name_qa\"],NoGravity:1b,PickupDelay:32767s,Item:{id:\"minecraft:dirt\",count:1}}");
        context.waitTicks(8);
        context.runOnClient(client -> {
            dev.arcaneclient.render.EntityEspRenderer.reset();
            dev.arcaneclient.render.EntityEspRenderer.tick(client);
            var labels = dev.arcaneclient.render.EntityEspRenderer.nameLabels();
            require(labels.contains("Diamond ×3"), "Nametags must refresh immediately after reset and label dropped stacks without Item ESP");
            require(labels.stream().noneMatch(label -> label.startsWith("Dirt")), "Item labels must respect nametag range");
        });
        context.waitTicks(3);
        context.runOnClient(client -> require(dev.arcaneclient.render.EntityEspRenderer.renderedNameCount() > 0,
            "Dropped-item labels must reach the render pass"));
        context.takeScreenshot("dropped-item-nametag");
        singleplayer.getServer().runCommand("kill @e[type=minecraft:item,tag=arcane_name_qa]");
        context.waitTicks(8);
        context.runOnClient(client -> {
            require(dev.arcaneclient.render.EntityEspRenderer.nameLabels().isEmpty(), "Removed items must not leave stale labels");
            ArcaneClient.config().nametags = false;
        });
        context.runOnClient(client -> {
            require(client.player != null, "Chunk tile test needs a player");
            ChunkPos chunk = client.player.chunkPosition();
            BlockPosition first = new BlockPosition(chunk.getMinBlockX() + 4, client.player.getBlockY(), chunk.getMinBlockZ() + 4);
            BlockPosition second = new BlockPosition(chunk.getMinBlockX() + 10, client.player.getBlockY(), chunk.getMinBlockZ() + 10);
            ArcaneClient.config().enabled = true;
            ArcaneClient.config().overlay = true;
            ArcaneClient.config().threshold = 1;
            ArcaneClient.config().grownBlocksRequired = 1;
            client.level.setBlock(new net.minecraft.core.BlockPos(second.x(), second.y(), second.z()),
                net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            ArcaneClient.engine().queueNearby(client);
            ArcaneClient.engine().settingsChanged(client);
        });
        context.waitFor(client -> !ArcaneClient.engine().tiles().isEmpty(), 200);
        context.waitTicks(5);
        context.runOnClient(client -> require(TraceRenderer.renderedTileCount() > 0, "Chunk tile renderer submitted no geometry"));
        context.takeScreenshot("chunk-tiles-enabled");
        int[] ground = new int[5];
        context.runOnClient(client -> {
            ChunkPos chunk = client.player.chunkPosition();
            ground[0] = chunk.x(); ground[1] = chunk.z();
            ground[2] = chunk.getMinBlockX() + 4; ground[3] = chunk.getMinBlockZ() + 4;
            ground[4] = client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground[2], ground[3]);
            require(Math.abs(TraceRenderer.renderedTileSurfaceY(chunk.x(), chunk.z(), 4, 4) - (ground[4] + 0.04)) < 0.001,
                "Tile must sit directly on the terrain");
        });
        singleplayer.getServer().runCommand("fill " + ground[2] + " " + ground[4] + " " + ground[3]
            + " " + (ground[2] + 3) + " " + (ground[4] + 3) + " " + (ground[3] + 3) + " minecraft:stone");
        context.waitTicks(20);
        context.runOnClient(client -> require(Math.abs(TraceRenderer.renderedTileSurfaceY(ground[0], ground[1], 4, 4)
            - (ground[4] + 4.04)) < 0.001, "Tile must refresh to match changed terrain"));
        singleplayer.getServer().runCommand("tp ArcaneQA " + (ground[2] + 0.5) + " " + (ground[4] + 40) + " " + (ground[3] + 0.5) + " 0 70");
        context.waitTicks(5);
        context.runOnClient(client -> {
            require(client.player.getY() > ground[4] + 30, "Test camera must be well above terrain");
            require(Math.abs(TraceRenderer.renderedTileSurfaceY(ground[0], ground[1], 4, 4) - (ground[4] + 4.04)) < 0.001,
                "Tile must not follow a flying player or elevated camera");
            require(Math.abs(TraceRenderer.renderedTileSurfaceY(ground[0], ground[1], 0, 0) - (ground[4] + 0.04)) < 0.001,
                "Adjacent columns must follow the lower ground, not the chunk's tallest block");
        });
        context.takeScreenshot("chunk-tiles-grounded-from-air");
        singleplayer.getServer().runCommand("tp ArcaneQA " + (ground[2] + 0.5) + " " + (ground[4] + 5) + " " + (ground[3] + 0.5));
        context.runOnClient(client -> {
            ArcaneClient.config().overlay = false;
            ArcaneClient.engine().clearCurrent();
        });
        singleplayer.getServer().runCommand("execute at ArcaneQA run fill ~-4 ~-1 ~-4 ~4 ~4 ~4 minecraft:deepslate hollow");
        singleplayer.getServer().runCommand("time set midnight");
        context.runOnClient(client -> ArcaneClient.config().fullbright = false);
        context.waitTicks(5);
        context.takeScreenshot("fullbright-disabled");
        context.runOnClient(client -> ArcaneClient.config().fullbright = true);
        context.waitTicks(5);
        context.takeScreenshot("fullbright-enabled");
        context.runOnClient(client -> ArcaneClient.config().fullbright = false);
        ArcaneClient.LOGGER.info("[QA] DEV UI: glass migration, toggles, favorites, combined filters, saved layout, search and resize passed");
        }
    }

    private static List<GuiModule> visibleModules(List<GuiCategory> categories) {
        return categories.stream().flatMap(category -> category.visible().stream()).toList();
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
