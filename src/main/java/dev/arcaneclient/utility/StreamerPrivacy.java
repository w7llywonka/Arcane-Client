package dev.arcaneclient.utility;

/** Central privacy policy so every new coordinate/server/player readout obeys Streamer Mode. */
public final class StreamerPrivacy {
    private StreamerPrivacy() {
    }

    public static boolean mayRevealSensitive(boolean streamerMode) {
        return !streamerMode;
    }

    public static String sensitiveValue(boolean streamerMode, String value) {
        return streamerMode ? "HIDDEN" : value;
    }

    public static String entityName(boolean streamerMode, boolean player, String actualName) {
        return streamerMode && player ? "PLAYER" : actualName;
    }
}
