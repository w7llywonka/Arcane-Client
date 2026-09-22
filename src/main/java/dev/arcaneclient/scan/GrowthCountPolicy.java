package dev.arcaneclient.scan;

/** One user-visible count threshold; there are no timing or multi-position gates. */
public final class GrowthCountPolicy {
    public static final int MAX_REQUIRED = 256;
    public static final int FLAG_SCORE = 50;
    private GrowthCountPolicy() {}
    public static int fromSensitivity(int percent) {
        int inverse = 100 - Math.clamp(percent, 0, 100);
        return Math.clamp(1 + (inverse * inverse + 39) / 40, 1, MAX_REQUIRED);
    }
    public static int sensitivity(int required) {
        return Math.clamp(100 - (int)Math.round(Math.sqrt((Math.clamp(required, 1, MAX_REQUIRED) - 1) * 40.0)), 0, 100);
    }
    public static int score(int count, int required) {
        return (int)Math.min(100L, Math.max(0L, count) * FLAG_SCORE / Math.clamp(required, 1, MAX_REQUIRED));
    }
}
