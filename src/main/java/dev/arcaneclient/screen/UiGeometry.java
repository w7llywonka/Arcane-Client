package dev.arcaneclient.screen;

/** Pure layout calculations shared by the HUD and Click GUI. */
public final class UiGeometry {
    private static final int MIN_RADAR_CELL = 15;
    private static final int RADAR_TEXT_PADDING = 4;

    private UiGeometry() {
    }

    public static int radarCellSize(int widestScoreWidth) {
        return Math.max(MIN_RADAR_CELL, Math.max(0, widestScoreWidth) + RADAR_TEXT_PADDING);
    }

    public static int labelWidth(int labelX, int labelRight) {
        return Math.max(1, labelRight - labelX);
    }
}
