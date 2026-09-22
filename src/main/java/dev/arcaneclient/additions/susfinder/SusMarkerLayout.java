package dev.arcaneclient.additions.susfinder;

/** Readable compact world labels with a bounded, approximately constant screen size. */
public final class SusMarkerLayout {
    private SusMarkerLayout() { }

    public static float scale(double distance) {
        if (Double.isNaN(distance)) return 0.025f;
        return (float)Math.clamp(distance * 0.005, 0.025, 1.5);
    }

    public static String title(int score) { return "SUS · " + Math.clamp(score, 0, 100) + "/100"; }

    public static String detail(int chunks, boolean inferred) {
        int count = Math.max(0, chunks);
        return count + (count == 1 ? " chunk · " : " chunks · ") + (inferred ? "inferred light" : "growth");
    }
}
