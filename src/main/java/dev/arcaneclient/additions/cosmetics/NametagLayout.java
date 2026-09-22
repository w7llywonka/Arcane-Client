package dev.arcaneclient.additions.cosmetics;

/** World-space clearance for the local headwear mesh and the billboard's downward text extent. */
public final class NametagLayout {
    public static final double HEADWEAR_GAP = 0.10;

    private NametagLayout() { }

    public static double headwearTopY(double eyeY, double bodyScale, int style, int headSize) {
        double tip = switch (style) { case 1 -> 0.28; case 2 -> 0.38; default -> 0.16; };
        return eyeY + bodyScale * (0.23 + tip * Math.clamp(headSize, 50, 150) / 100.0);
    }

    public static double textDrop(int tagScale, int fontHeight) {
        // Include the background edge below the glyphs, not just the font's baseline.
        return (Math.max(1, fontHeight) + 2) * 0.025 * Math.clamp(tagScale, 50, 200) / 100.0;
    }

    public static double anchorY(double defaultY, double eyeY, double bodyScale, boolean localHeadwear,
                                  int style, int headSize, int tagScale, int fontHeight) {
        if (!localHeadwear) return defaultY;
        return Math.max(defaultY, headwearTopY(eyeY, bodyScale, style, headSize)
            + textDrop(tagScale, fontHeight) + HEADWEAR_GAP);
    }
}
