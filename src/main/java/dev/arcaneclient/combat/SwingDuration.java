package dev.arcaneclient.combat;

/** Slow-only hand animation duration contract; vanilla's ordinary duration is six ticks. */
public final class SwingDuration {
    public static final int VANILLA_TICKS = 6;
    public static final int MIN_TICKS = 7;
    public static final int DEFAULT_TICKS = 12;
    public static final int MAX_TICKS = 30;

    private SwingDuration() {
    }

    public static int clamp(int ticks) {
        return Math.clamp(ticks, MIN_TICKS, MAX_TICKS);
    }
}
