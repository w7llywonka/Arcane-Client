package dev.arcaneclient.additions.social;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import dev.arcaneclient.screen.TextSettingsScreen;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Opt-in, rate-limited commands. Incoming requests are exact system-message matches. */
public final class AutoTpa {
    private static long nextRequest, nextAccept;
    private static String lastTemplate;
    private static Pattern requestPattern;
    private static boolean wasSending;
    private AutoTpa() {}

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) receive(Minecraft.getInstance(), message);
        });
    }

    public static void reset(Minecraft client) {
        nextRequest = nextAccept = 0;
        wasSending = false;
    }

    private static boolean ready(Minecraft client) {
        return client.player != null && client.level != null && client.getConnection() != null
            && client.gui.screen() == null && !client.isPaused() && client.player.isAlive();
    }

    public static void tick(Minecraft client) {
        var config = ArcaneClient.config().social;
        boolean sending = config.autoTpa && config.sendRequests && ready(client)
            && !SocialConfig.player(config.recipient).isEmpty();
        long now = System.nanoTime();
        if (!sending) { wasSending = false; return; }
        // Give the user a full interval after enabling, connecting, or leaving a menu.
        if (!wasSending) nextRequest = now + Math.clamp(config.requestInterval, 30, 600) * 1_000_000_000L;
        wasSending = true;
        if (now < nextRequest) return;
        nextRequest = now + Math.clamp(config.requestInterval, 30, 600) * 1_000_000_000L;
        if (!client.player.getName().getString().equalsIgnoreCase(config.recipient))
            client.getConnection().sendCommand("tpa " + SocialConfig.player(config.recipient));
    }

    private static void receive(Minecraft client, Component message) {
        var settings = ArcaneClient.config().social;
        if (!settings.autoTpa || settings.sendRequests || !ready(client)) return;
        Pattern pattern = pattern(settings.requestMessage);
        if (pattern == null || System.nanoTime() < nextAccept) return;
        String text = message.getString().replaceAll("§.", "").trim();
        if (text.length() > 240) return;
        var match = pattern.matcher(text);
        if (!match.matches()) return;
        String requester = match.group(1);
        boolean trusted = Arrays.stream(settings.trustedPlayers.split("[,\\s]+"))
            .anyMatch(name -> name.equalsIgnoreCase(requester));
        if (!trusted || requester.equalsIgnoreCase(client.player.getName().getString())) return;
        nextAccept = System.nanoTime() + 10_000_000_000L;
        // Never send a bare /tpaccept: it could accept a newer request from someone else.
        client.getConnection().sendCommand("tpaccept " + requester);
    }

    private static Pattern pattern(String value) {
        if (java.util.Objects.equals(lastTemplate, value)) return requestPattern;
        lastTemplate = value;
        requestPattern = null;
        if (value == null || value.length() > 200) return null;
        int index = value.indexOf("{player}");
        if (index < 0 || index != value.lastIndexOf("{player}")) return null;
        requestPattern = Pattern.compile("^" + Pattern.quote(value.substring(0, index))
            + "([A-Za-z0-9_]{3,16})" + Pattern.quote(value.substring(index + 8)) + "$", Pattern.CASE_INSENSITIVE);
        return requestPattern;
    }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        var c = config.social;
        return List.of(GuiModule.toggle("Auto TPA", "Accept named requests from trusted players, or periodically request a teleport.",
                () -> c.autoTpa, value -> { c.autoTpa = value; reset(client); })
            .with(new GuiSetting.Cycle("Mode", () -> c.sendRequests ? "Send requests" : "Trusted accept", () -> {
                c.sendRequests = !c.sendRequests; reset(client);
            }))
            .with(new GuiSetting.Cycle("Players and message", () -> "Configure", () -> client.gui.setScreen(
                new TextSettingsScreen(client.gui.screen(), config, "Auto TPA", "Uses server /tpa and named /tpaccept commands.", List.of(
                    new TextSettingsScreen.Field("Request recipient", "Used only in Send requests mode.", () -> c.recipient, s -> c.recipient = s, 16),
                    new TextSettingsScreen.Field("Trusted players", "Comma-separated usernames. Empty means accept nobody.", () -> c.trustedPlayers, s -> c.trustedPlayers = s, 1200),
                    new TextSettingsScreen.Field("Exact request message", "Use {player} once. Copy your server's system-message wording.", () -> c.requestMessage, s -> c.requestMessage = s, 200)
                )))))
            .with(new GuiSetting.Slider("Request interval", () -> c.requestInterval, value -> c.requestInterval = value, 30, 600, "s"))
            .with(new GuiSetting.Info("Acceptance", () -> "Trusted names only; 10s cooldown"))
            .with(new GuiSetting.Info("Message format", () -> pattern(c.requestMessage) == null ? "Needs one {player}" : "Exact match"))
            .build());
    }
}
