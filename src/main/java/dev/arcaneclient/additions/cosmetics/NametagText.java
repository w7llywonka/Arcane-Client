package dev.arcaneclient.additions.cosmetics;

/** Formatting kept independent of the game so redaction and field combinations can be checked. */
public final class NametagText {
    private NametagText() { }

    public static String player(NametagConfig config, String name, boolean redact, boolean badge,
                                int health, int distance) {
        StringBuilder text = new StringBuilder();
        if (badge) field(text, "ARC");
        if (config.name) field(text, redact ? "PLAYER" : name);
        if (config.health) field(text, Math.max(0, health) + " HP");
        if (config.distance) field(text, Math.max(0, distance) + "m");
        return text.toString();
    }

    public static String item(NametagConfig config, String name, int count, int distance) {
        StringBuilder text = new StringBuilder();
        if (config.name) field(text, name);
        if (config.stackCount) {
            if (!text.isEmpty()) text.append(' ');
            text.append('×').append(Math.max(1, count));
        }
        if (config.itemDistance) field(text, Math.max(0, distance) + "m");
        return text.toString();
    }

    private static void field(StringBuilder target, String value) {
        if (!target.isEmpty()) target.append("  ");
        target.append(value);
    }
}
