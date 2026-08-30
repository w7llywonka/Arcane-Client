package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Disconnects from the active multiplayer server and immediately starts a vanilla reconnect. */
@Environment(EnvType.CLIENT)
public final class RelogController {
    private static final long RETRY_GUARD_NANOS = 1_000_000_000L;
    private static long lastAttemptNanos = Long.MIN_VALUE;

    private RelogController() {
    }

    public static Result relog(MinecraftClient client) {
        if (client.world == null || client.player == null || client.isInSingleplayer()) {
            return Result.MULTIPLAYER_ONLY;
        }

        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || server.address == null || server.address.isBlank()) {
            return Result.NO_SERVER;
        }
        if (server.isRealm()) {
            return Result.REALMS_UNSUPPORTED;
        }
        if (!ServerAddress.isValid(server.address)) {
            return Result.INVALID_ADDRESS;
        }

        long now = System.nanoTime();
        if (lastAttemptNanos != Long.MIN_VALUE && now - lastAttemptNanos < RETRY_GUARD_NANOS) {
            return Result.TOO_FAST;
        }
        lastAttemptNanos = now;

        ServerAddress address = ServerAddress.parse(server.address);
        MultiplayerScreen parent = new MultiplayerScreen(new TitleScreen());
        try {
            client.disconnect(parent, false);
            ConnectScreen.connect(parent, client, address, server, false, null);
            ArcaneClient.LOGGER.info("Relogging to {}", server.address);
            return Result.STARTED;
        } catch (RuntimeException exception) {
            ArcaneClient.LOGGER.error("Could not relog to {}", server.address, exception);
            return Result.FAILED;
        }
    }

    public enum Result {
        STARTED("Relogging…"),
        MULTIPLAYER_ONLY("Relog only works on multiplayer servers"),
        NO_SERVER("No multiplayer server to rejoin"),
        REALMS_UNSUPPORTED("Relog does not support Realms"),
        INVALID_ADDRESS("The current server address is invalid"),
        TOO_FAST("Relog is already in progress"),
        FAILED("Relog failed; open Multiplayer to reconnect");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return this.message;
        }
    }
}
