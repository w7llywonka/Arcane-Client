package dev.arcaneclient.additions.social;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SocialConfig {
    public boolean autoTpa;
    public boolean sendRequests;
    public String recipient = "";
    public String trustedPlayers = "";
    public String requestMessage = "{player} has requested to teleport to you.";
    public int requestInterval = 60;
    public void sanitize() {
        recipient = player(recipient);
        trustedPlayers = trustedPlayers == null ? "" : Arrays.stream(trustedPlayers.split("[,\\s]+"))
            .map(SocialConfig::player).filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT))
            .distinct().limit(64).collect(Collectors.joining(", "));
        requestMessage = requestMessage == null ? "" : requestMessage.replaceAll("[\\p{Cntrl}§]", "").trim();
        if (requestMessage.length() > 200) requestMessage = requestMessage.substring(0, 200);
        requestInterval = Math.clamp(requestInterval, 30, 600);
    }
    public static String player(String value) {
        return value != null && value.trim().matches("[A-Za-z0-9_]{3,16}") ? value.trim() : "";
    }
}
