package dev.arcaneclient.render;

import java.util.Locale;

/** Pure classification and retention rules for bounded world-intel overlays. */
public final class WorldIntelPolicy {
    public enum SearchKind {
        NONE,
        SEARCH,
        PORTAL
    }

    private WorldIntelPolicy() {
    }

    public static SearchKind classify(String blockPath) {
        String path = blockPath == null ? "" : blockPath.toLowerCase(Locale.ROOT);
        if (path.equals("nether_portal") || path.equals("end_portal") || path.equals("end_portal_frame")) {
            return SearchKind.PORTAL;
        }
        if (path.equals("spawner") || path.equals("trial_spawner") || path.equals("vault")
            || path.equals("beacon") || path.equals("lodestone") || path.equals("respawn_anchor")
            || path.equals("ancient_debris")) {
            return SearchKind.SEARCH;
        }
        return SearchKind.NONE;
    }

    public static boolean recordBreadcrumb(double squaredDistance, boolean dimensionChanged) {
        return dimensionChanged || squaredDistance >= 6.25;
    }

    public static boolean probableLogout(
        boolean previouslyVisible,
        boolean stillVisible,
        boolean chunkStillLoaded,
        boolean worldChanged,
        boolean localPlayer
    ) {
        return previouslyVisible && !stillVisible && chunkStillLoaded && !worldChanged && !localPlayer;
    }

    public static boolean liveSpot(long nowMillis, long createdMillis, int lifetimeMinutes) {
        long lifetime = Math.max(1, lifetimeMinutes) * 60_000L;
        return nowMillis >= createdMillis && nowMillis - createdMillis <= lifetime;
    }
}
