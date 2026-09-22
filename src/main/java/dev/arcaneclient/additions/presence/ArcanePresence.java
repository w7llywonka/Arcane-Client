package dev.arcaneclient.additions.presence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.SessionService;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Explicit opt-in, ephemeral account presence. Never uploads Minecraft credentials to Arcane. */
public final class ArcanePresence {
    private static final String ORIGIN = "https://arcane-discord-bot-production.up.railway.app/api/presence/";
    private static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Arcane-presence");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static volatile Map<UUID, Long> online = Map.of();
    private static volatile String status = "Off";
    private static long generation, nextAttempt;
    private static boolean wanted, busy, stopped;
    private static String token = "";
    private static UUID identity;
    private static int failures;

    private ArcanePresence() {}
    private record Identity(UUID uuid, String name, String accessToken, SessionService service) {}

    public static void register() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stop();
            synchronized (LOCK) { stopped = true; }
            IO.shutdown();
        });
    }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        var c = config.presence;
        return List.of(GuiModule.toggle("Arcane Presence",
                "Opt in to an ARC badge visible to other updated Arcane clients using Presence. Never posts in server chat.",
                () -> c.enabled, value -> {
                    if (!value) { c.enabled = false; stop(); return; }
                    var parent = client.gui.screen();
                    client.gui.setScreen(new ConfirmScreen(confirmed -> {
                        c.enabled = confirmed;
                        config.save();
                        client.gui.setScreen(parent);
                    }, Component.literal("Share Arcane presence?"), Component.literal(
                        "While in multiplayer, your public Minecraft UUID is shared with Arcane's presence service and other opted-in users. "
                        + "Minecraft verifies ownership directly with Mojang; Arcane never receives your login token. "
                        + "No server address, coordinates, chat, or other player list is uploaded. "
                        + "Turn this off to withdraw; offline badges expire within 2 minutes. Streamer Mode also stops sharing.")));
                })
            .with(new GuiSetting.Toggle("Show ARC badges", () -> c.showBadges, value -> c.showBadges = value))
            .with(new GuiSetting.Swatch("Badge color", () -> c.badgeColor, value -> c.badgeColor = value))
            .with(new GuiSetting.Info("Status", () -> status))
            .with(new GuiSetting.Info("Privacy", () -> "UUID only · expires in 2 minutes"))
            .build());
    }

    public static void tick(Minecraft client) {
        ArcaneConfig c = ArcaneClient.config();
        var session = client.getUser();
        UUID uuid = session.getProfileId();
        boolean ready = c.presence.enabled && !c.streamerMode && !c.cleanCapture
            && client.level != null && client.player != null && client.getConnection() != null
            && !client.isLocalServer() && uuid != null && uuid.equals(client.player.getUUID());
        if (!ready) {
            stop();
            status = !c.presence.enabled ? "Off" : c.streamerMode || c.cleanCapture ? "Sharing paused for privacy"
                : client.level == null || client.isLocalServer() ? "Waiting for multiplayer" : "Online account required";
            return;
        }
        synchronized (LOCK) {
            if (stopped) return;
            if (!wanted || !uuid.equals(identity)) {
                stop();
                wanted = true;
                identity = uuid;
                failures = 0;
                nextAttempt = System.nanoTime() + 5_000_000_000L;
                status = "Starting in 5 seconds";
            }
            if (busy || System.nanoTime() < nextAttempt) return;
            busy = true;
            long current = generation;
            Identity credentials = new Identity(uuid, session.getName(), session.getAccessToken(), client.services().sessionService());
            IO.execute(() -> update(current, credentials));
        }
    }

    /** Called on explicit opt-out, disconnect, or profile changes. Never blocks the game thread. */
    public static void stop() {
        synchronized (LOCK) {
            if (!wanted && token.isEmpty()) return;
            wanted = false;
            generation++;
            identity = null;
            online = Map.of();
            String previous = token;
            token = "";
            status = "Off";
            if (!previous.isEmpty() && !IO.isShutdown()) IO.execute(() -> withdraw(previous));
        }
    }

    private static boolean current(long id) {
        synchronized (LOCK) { return wanted && !stopped && generation == id; }
    }

    private static void update(long id, Identity credentials) {
        String activeToken = "";
        try {
            if (!current(id)) return;
            synchronized (LOCK) { activeToken = token; }
            if (activeToken.isEmpty()) {
                JsonObject player = new JsonObject();
                player.addProperty("uuid", credentials.uuid.toString());
                player.addProperty("name", credentials.name);
                JsonObject challenge = request("challenge", "POST", "", player);
                String nonce = challenge.get("challenge").getAsString();
                if (!nonce.matches("[a-f0-9]{40}")) throw new IOException("Invalid challenge");
                if (!current(id)) return;
                // Authlib sends this credential only to Mojang, not the Arcane relay.
                credentials.service.joinServer(credentials.uuid, credentials.accessToken, nonce);
                if (!current(id)) return;
                JsonObject proof = new JsonObject();
                proof.addProperty("challenge", nonce);
                activeToken = request("verify", "POST", "", proof).get("token").getAsString();
                if (!activeToken.matches("[a-f0-9]{64}")) throw new IOException("Invalid session");
                synchronized (LOCK) {
                    if (current(id)) token = activeToken;
                }
            }
            if (!current(id)) { withdraw(activeToken); return; }
            request("heartbeat", "POST", activeToken, new JsonObject());
            if (!current(id)) { withdraw(activeToken); return; }
            JsonObject feed = request("online", "GET", activeToken, null);
            Map<UUID, Long> received = new HashMap<>();
            var entries = feed.getAsJsonArray("users");
            if (entries == null || entries.size() > 4096) throw new IOException("Invalid presence response");
            long now = System.nanoTime();
            for (var element : entries) {
                var entry = element.getAsJsonObject();
                String raw = entry.get("uuid").getAsString();
                int ttl = entry.get("expiresIn").getAsInt();
                if (!raw.matches("[a-f0-9]{32}") || ttl < 1 || ttl > 120) continue;
                UUID player = UUID.fromString(raw.substring(0,8) + "-" + raw.substring(8,12) + "-" + raw.substring(12,16)
                    + "-" + raw.substring(16,20) + "-" + raw.substring(20));
                received.put(player, now + ttl * 1_000_000_000L);
            }
            synchronized (LOCK) {
                if (current(id)) {
                    online = Map.copyOf(received);
                    status = "Sharing · ARC badge active";
                    failures = 0;
                    nextAttempt = System.nanoTime() + 35_000_000_000L;
                }
            }
        } catch (Exception error) {
            synchronized (LOCK) {
                if (current(id)) {
                    failures = Math.min(failures + 1, 4);
                    int delay = Math.min(300, 30 << failures);
                    nextAttempt = System.nanoTime() + delay * 1_000_000_000L;
                    online = Map.of();
                    status = error instanceof ServiceError service && service.code == 404 ? "Presence service not deployed"
                        : error instanceof ServiceError service && service.code == 401 ? "Rechecking account next retry"
                        : "Presence unavailable · retry " + delay + "s";
                    if (error instanceof ServiceError service && service.code == 401) token = "";
                }
            }
            // Never log exception text: authentication exceptions can contain sensitive details.
        } finally {
            if (!current(id) && !activeToken.isEmpty()) withdraw(activeToken);
            synchronized (LOCK) { busy = false; }
        }
    }

    private static JsonObject request(String endpoint, String method, String bearer, JsonObject data) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ORIGIN + endpoint))
            .timeout(Duration.ofSeconds(9)).header("Accept", "application/json");
        if (!bearer.isEmpty()) request.header("Authorization", "Bearer " + bearer);
        request.method(method, data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(data.toString()));
        if (data != null) request.header("Content-Type", "application/json");
        PresenceResponse receiver = new PresenceResponse(endpoint.equals("online") ? 300_000 : 4096);
        var future = HTTP.sendAsync(request.build(), info -> receiver);
        try {
            var response = future.get(10, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new ServiceError(response.statusCode());
            return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IOException("Presence request unavailable");
        } finally {
            receiver.cancel();
            future.cancel(true);
        }
    }
    private static void withdraw(String bearer) {
        try { request("session", "DELETE", bearer, null); }
        catch (Exception ignored) { /* Two-minute server expiry is the offline fallback. */ }
    }
    private static final class ServiceError extends IOException {
        final int code;
        ServiceError(int code) { super("Presence HTTP status"); this.code = code; }
    }

    public static boolean hasBadge(UUID uuid) {
        ArcaneConfig c = ArcaneClient.config();
        return c != null && c.presence.enabled && c.presence.showBadges && !c.streamerMode
            && !ArcaneVisibility.overlaysHidden() && online.getOrDefault(uuid, 0L) > System.nanoTime();
    }
    public static Component tabName(PlayerInfo entry, Component original) {
        if (!hasBadge(entry.getProfile().id())) return original;
        return Component.literal("ARC ").withStyle(style -> style.withColor(ArcaneClient.config().presence.badgeColor & 0xFFFFFF))
            .append(original.copy());
    }
}
