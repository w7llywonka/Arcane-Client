package dev.arcaneclient.freecam;

/** Pure scroll-wheel speed adjustment for Freecam. */
public final class FreecamSpeed {
    private FreecamSpeed() {
    }

    public static int adjust(int current, double verticalScroll) {
        if (verticalScroll == 0.0) return Math.clamp(current, 1, 20);
        return Math.clamp(current + (verticalScroll > 0.0 ? 1 : -1), 1, 20);
    }
}
