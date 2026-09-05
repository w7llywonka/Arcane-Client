package dev.arcaneclient.utility;

import java.util.Locale;

/** Pure state decisions for input automation and clipboard/death-coordinate formatting. */
public final class QualityOfLifePolicy {
    private QualityOfLifePolicy() {
    }

    public static boolean forceHeldInput(boolean enabled, boolean worldReady, boolean screenOpen) {
        return enabled && worldReady && !screenOpen;
    }

    public static boolean forceJump(boolean enabled, boolean worldReady, boolean screenOpen, boolean moving, boolean grounded) {
        return forceHeldInput(enabled, worldReady, screenOpen) && moving && grounded;
    }

    public static boolean guardDurability(boolean enabled, int damage, int maxDamage, int threshold) {
        return enabled && SafetyRules.durabilityCritical(damage, maxDamage, threshold);
    }

    public static String coordinates(double x, double y, double z) {
        return String.format(Locale.ROOT, "%d %d %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
