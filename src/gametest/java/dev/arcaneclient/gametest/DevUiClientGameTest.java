package dev.arcaneclient.gametest;

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
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.util.math.ChunkPos;

/** Isolated UI smoke test; does not connect to a server or touch a user's game instance. */
public final class DevUiClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
        singleplayer.getClientWorld().waitForChunksRender();
        context.getInput().resizeWindow(1600, 1000);
        context.runOnClient(client -> {
            client.options.getGuiScale().setValue(2);
            client.onResolutionChanged();
            ArcaneClient.config().uiTheme = 0;
            ArcaneClient.config().customUiColors = false;
            ArcaneClient.config().devUiRevision = 1;
            ArcaneClient.config().uiBackgroundDimPercent = 42;
            ArcaneClient.config().uiCornerRadius = 12;
            client.setScreen(new ArcaneSettingsScreen(null));
        });
        context.waitTicks(8);
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.currentScreen;
            List<GuiCategory> categories = field(screen, "categories");
            require(categories.getFirst().name().equals("BASE FINDING"), "Base Finding must be first");
            require(categories.stream().allMatch(GuiCategory::open), "All categories start open");
            require(categories.stream().map(GuiCategory::y).distinct().count() == 1, "Desktop categories must share one top row");
            require(categories.stream().allMatch(c -> c.y() + c.lastHeight() < screen.height * 0.55), "Desktop panels leave the lower world visible");
            require(ArcaneClient.config().uiOpacityPercent == 100, "Panels must start opaque");
            require(ArcaneClient.config().uiBackgroundDimPercent == 0, "Old dim setting must migrate to a clear world");
            require(client.world != null, "Overlay test must run over an actual world");
            require(!screen.shouldPause(), "Overlay must leave the world running");
            GuiCategory first = categories.getFirst();
            UiGeometry.ClickGuiMetrics metrics = field(screen, "metrics");
            GuiModule module = first.visible().getFirst();
            boolean original = module.enabled();
            int x = first.x() + 14;
            int y = first.y() + metrics.headerHeight() + metrics.moduleHeight() / 2;
            screen.mouseClicked(new Click(x, y, new MouseInput(0, 0)), false);
            require(module.enabled() != original, "Left click must toggle");
            screen.mouseClicked(new Click(x, y, new MouseInput(0, 0)), false);
            screen.mouseClicked(new Click(x, y, new MouseInput(1, 0)), false);
            require(module.expanded(), "Right click must expand settings");
            screen.mouseClicked(new Click(x, y, new MouseInput(1, 0)), false);
            screen.mouseClicked(new Click(first.x() + metrics.windowWidth() - 13, y, new MouseInput(0, 0)), false);
            require(!module.expanded() && module.enabled() != original, "Clicking the visible switch must toggle, not open settings");
            screen.mouseClicked(new Click(first.x() + metrics.windowWidth() - 13, y, new MouseInput(0, 0)), false);
            TextFieldWidget search = field(screen, "searchInput");
            search.setText("freecam");
            require(categories.stream().flatMap(c -> c.visible().stream()).anyMatch(m -> m.name().equals("Freecam")), "Search must find Freecam");
            search.setText("");
        });
        context.waitTicks(5);
        context.takeScreenshot("dev-ui-wide");
        context.runOnClient(client -> {
            List<GuiCategory> categories = field(client.currentScreen, "categories");
            for (GuiCategory category : categories) category.scrollBy(category.maxScroll());
        });
        context.waitTicks(3);
        context.takeScreenshot("dev-ui-rounded-scrolled-bottom");
        context.getInput().resizeWindow(960, 640);
        context.waitTicks(5);
        context.runOnClient(client -> {
            ArcaneSettingsScreen screen = (ArcaneSettingsScreen) client.currentScreen;
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
        context.runOnClient(client -> client.setScreen(null));
        context.runOnClient(client -> {
            ArcaneClient.config().nametags = true;
            ArcaneClient.config().nametagRange = 16;
            ArcaneClient.config().itemEsp = false;
            client.player.setYaw(0); client.player.setPitch(0);
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
            require(!ArcaneClient.engine().tiles().isEmpty(), "Live completed scans produced no chunk tiles");
            ChunkPos chunk = client.player.getChunkPos();
            BlockPosition first = new BlockPosition(chunk.getStartX() + 4, client.player.getBlockY(), chunk.getStartZ() + 4);
            BlockPosition second = new BlockPosition(chunk.getStartX() + 10, client.player.getBlockY(), chunk.getStartZ() + 10);
            ArcaneClient.config().enabled = true;
            ArcaneClient.config().overlay = true;
            ArcaneClient.config().threshold = 1;
            ArcaneClient.engine().mergeStatic(chunk.x, chunk.z, ScanResult.builder()
                .addStatic(SignalCategory.NATURAL_GROWTH, EvidenceFamily.GROWTH, first, "tile render test growth", 100)
                .addStatic(SignalCategory.CULTIVATION, EvidenceFamily.FARM_GEOMETRY, second, "tile render test farm geometry", 100)
                .build());
            ArcaneClient.engine().settingsChanged(client);
            require(!ArcaneClient.engine().markers().isEmpty(), "Chunk tile test could not create a scored marker");
            client.options.hudHidden = true;
        });
        context.waitTicks(5);
        context.runOnClient(client -> require(TraceRenderer.renderedTileCount() > 0, "Chunk tile renderer submitted no geometry"));
        context.takeScreenshot("chunk-tiles-enabled");
        int[] ground = new int[5];
        context.runOnClient(client -> {
            ChunkPos chunk = client.player.getChunkPos();
            ground[0] = chunk.x; ground[1] = chunk.z;
            ground[2] = chunk.getStartX() + 4; ground[3] = chunk.getStartZ() + 4;
            ground[4] = client.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ground[2], ground[3]);
            require(Math.abs(TraceRenderer.renderedTileSurfaceY(chunk.x, chunk.z, 4, 4) - (ground[4] + 0.04)) < 0.001,
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
            client.options.hudHidden = false;
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
        ArcaneClient.LOGGER.info("[QA] DEV UI: rendering, toggles, settings, search and resize passed");
        }
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
