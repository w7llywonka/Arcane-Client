package dev.arcaneclient.utility;

/** Pure boost gating kept separate so flight behavior stays deterministic and testable. */
final class ElytraAssistLogic {
    private ElytraAssistLogic() {
    }

    static boolean shouldBoost(
        boolean enabled,
        boolean gliding,
        boolean inputBlocked,
        int cooldownTicks,
        double speedBlocksPerSecond,
        boolean smartConservation,
        int boostBelowBlocksPerSecond
    ) {
        if (!enabled || !gliding || inputBlocked || cooldownTicks > 0) return false;
        return !smartConservation || speedBlocksPerSecond <= boostBelowBlocksPerSecond;
    }
}
