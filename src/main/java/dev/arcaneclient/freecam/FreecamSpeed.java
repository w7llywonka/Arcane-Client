package dev.arcaneclient.freecam;

/** Pure scroll-wheel speed adjustment for Freecam. */
public final class FreecamSpeed {
    public static final int MIN = 1;
    public static final int MAX = 75;

    private FreecamSpeed() {
    }

    public static int adjust(int current, double verticalScroll) {
        if (verticalScroll == 0.0) return Math.clamp(current, MIN, MAX);
        return Math.clamp(current + (verticalScroll > 0.0 ? 1 : -1), MIN, MAX);
    }

    public static double blocksPerTick(int configuredSpeed) {
        return Math.clamp(configuredSpeed, MIN, MAX) / 10.0;
    }
}
