package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;

/** A single vanilla leave/connect cycle, entirely client-side. No companion mod. */
@Environment(EnvType.CLIENT)
public final class RelogController {
    private static final long OFFLINE_DELAY_NANOS = 2_000_000_000L;
    private enum Phase { IDLE, LEAVE, WAIT, CONNECT }
    private static Phase phase = Phase.IDLE;
    private static ServerInfo target;
    private static CountdownScreen countdown;
    private static long disconnectedAt;

    private RelogController() { }

    public static Result relog(MinecraftClient client) {
        if (isPending()) return Result.TOO_FAST;
        if (client == null || client.world == null || client.player == null
            || client.isInSingleplayer() || client.getNetworkHandler() == null) {
            return Result.MULTIPLAYER_ONLY;
        }
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || server.address == null || server.address.isBlank() || server.isRealm()) {
            return Result.MULTIPLAYER_ONLY;
        }
        target = server;
        // Defer teardown until the next tick, not inside a keybind/widget callback.
        phase = Phase.LEAVE;
        return Result.STARTED;
    }

    public static boolean tick(MinecraftClient client) {
        if (!isPending()) return false;
        try {
            if (phase == Phase.LEAVE) {
                // Vanilla Leave Server: close the connection and finish world/resource
                // cleanup BEFORE the offline delay begins.
                client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
                disconnectedAt = System.nanoTime();
                countdown = new CountdownScreen();
                phase = Phase.WAIT;
                client.setScreen(countdown);
            } else if (phase == Phase.WAIT) {
                if (client.currentScreen != countdown || client.world != null) {
                    cancel();
                } else if (System.nanoTime() - disconnectedAt >= OFFLINE_DELAY_NANOS) {
                    ServerInfo server = target;
                    phase = Phase.CONNECT;
                    ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client,
                        ServerAddress.parse(server.address), server, false, null);
                }
            } else if (phase == Phase.CONNECT) {
                if (client.world != null || client.currentScreen instanceof DisconnectedScreen
                    || client.currentScreen instanceof MultiplayerScreen
                    || client.currentScreen instanceof TitleScreen) {
                    // Preserve vanilla errors/cancellation; never loop on a rejection,
                    // stale proxy session, ban or authentication failure.
                    cancel();
                }
            }
        } catch (RuntimeException exception) {
            cancel();
            ArcaneClient.LOGGER.error("Relog connection cycle failed", exception);
            client.setScreen(new DisconnectedScreen(new MultiplayerScreen(new TitleScreen()),
                Text.literal("Relog failed"), Text.literal("Could not reconnect. Please join from the server list.")));
        }
        return isPending();
    }

    public static void onWorldChange(ClientWorld world) {
        // Null is expected during our own disconnect; retain the reconnect target.
        if (world != null) cancel();
    }

    public static void cancel() {
        phase = Phase.IDLE;
        target = null;
        countdown = null;
    }

    public static boolean isPending() { return phase != Phase.IDLE; }

    public static String statusLabel() {
        return switch (phase) {
            case LEAVE -> "LEAVING";
            case WAIT -> "WAITING";
            case CONNECT -> "JOINING";
            case IDLE -> "READY";
        };
    }

    private static final class CountdownScreen extends Screen {
        private CountdownScreen() { super(Text.literal("Relog")); }

        @Override
        protected void init() {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(width / 2 - 100, height / 2 + 20, 200, 20).build());
        }

        @Override
        public void close() {
            cancel();
            client.setScreen(new MultiplayerScreen(new TitleScreen()));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            int seconds = (int) Math.max(0, Math.ceil(
                (OFFLINE_DELAY_NANOS - (System.nanoTime() - disconnectedAt)) / 1_000_000_000.0));
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Reconnecting in " + seconds + "s…"), width / 2, height / 2 - 20, 0xFFFFFFFF);
        }
    }

    public enum Result {
        STARTED("Disconnecting, then reconnecting in two seconds"),
        MULTIPLAYER_ONLY("Relog requires an active multiplayer server connection (not singleplayer or Realms)"),
        TOO_FAST("Relog is already in progress");

        private final String message;
        Result(String message) { this.message = message; }
        public String message() { return message; }
    }
}
