package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.utility.RelogController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;
import java.util.Properties;

/** Real socket connections to an isolated server; the external Rejoin mod is absent. */
@SuppressWarnings("UnstableApiUsage")
public final class RelogClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(640, 360);
        Properties properties = new Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("server-port", "25586");
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            server.connect();
            context.waitFor(client -> client.player != null && client.currentScreen == null);
            String address = context.computeOnClient(client -> client.getCurrentServerEntry().address);
            Object previousWorld = context.computeOnClient(client -> client.world);
            context.runOnClient(client -> {
                require(RelogController.relog(client) == RelogController.Result.STARTED, "start");
                require(RelogController.relog(client) == RelogController.Result.TOO_FAST, "duplicate press");
            });
            context.waitFor(client -> client.world == null && RelogController.statusLabel().equals("WAITING"));
            long offlineStart = System.nanoTime();
            context.waitFor(client -> client.world != null && client.player != null && client.currentScreen == null, 5000);
            require(System.nanoTime() - offlineStart >= 1_900_000_000L, "must wait two real seconds offline");
            context.runOnClient(client -> {
                require(client.world != previousWorld, "must create a new world connection, not send a command");
                require(address.equals(client.getCurrentServerEntry().address), "same server address and port");
                require(!RelogController.isPending(), "success clears pending state");
            });
            require(server.computeOnServer(s -> s.getCurrentPlayerCount()) == 1, "one server player after reconnect");

            // Test the actual registered keybinding, not just the controller entry point.
            context.runOnClient(client -> {
                ArcaneClient.keybinds().relog().setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F10));
                KeyBinding.updateKeysByCode();
                KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F10));
            });
            context.waitFor(client -> client.world == null && RelogController.statusLabel().equals("WAITING"));
            context.clickScreenButton("gui.cancel");
            context.runOnClient(client -> {
                require(!RelogController.isPending(), "cancel clears pending state");
                require(client.currentScreen instanceof MultiplayerScreen, "cancel returns to server list");
            });
            long cancelledAt = System.nanoTime();
            context.waitFor(client -> System.nanoTime() - cancelledAt >= 2_200_000_000L, 5000);
            context.runOnClient(client -> require(client.world == null, "cancel must not rejoin later"));

            server.connect();
            context.waitFor(client -> client.player != null && client.currentScreen == null);
            // An unreachable loopback port must leave a useful error and stop, not spam retries.
            context.runOnClient(client -> {
                client.getCurrentServerEntry().address = "127.0.0.1:1";
                require(RelogController.relog(client) == RelogController.Result.STARTED, "failed connection start");
            });
            context.waitFor(client -> client.currentScreen instanceof DisconnectedScreen && !RelogController.isPending(), 5000);
            long failedAt = System.nanoTime();
            context.waitFor(client -> System.nanoTime() - failedAt >= 2_200_000_000L, 5000);
            context.runOnClient(client -> require(client.currentScreen instanceof DisconnectedScreen, "preserve failure screen"));
            ArcaneClient.LOGGER.info("[QA] Built-in Relog passed: real disconnect/rejoin, two-second delay, duplicate guard, keybind, cancellation and connection failure; no Rejoin mod");
        } finally {
            context.runOnClient(client -> {
                RelogController.cancel();
                ArcaneClient.keybinds().relog().setBoundKey(InputUtil.UNKNOWN_KEY);
                KeyBinding.updateKeysByCode();
                if (client.world != null) client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
                client.setScreen(new TitleScreen());
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Relog: " + message);
    }
}
