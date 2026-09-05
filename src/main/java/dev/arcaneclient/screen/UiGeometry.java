package dev.arcaneclient.screen;

/** Pure layout calculations shared by the HUD and Click GUI. */
public final class UiGeometry {
    private static final int MIN_RADAR_CELL = 15;
    private static final int RADAR_TEXT_PADDING = 4;
    private static final int CLICK_GUI_MIN_WINDOW_WIDTH = 104;
    private static final int CLICK_GUI_MAX_WINDOW_WIDTH = 148;

    private UiGeometry() {
    }

    public static int radarCellSize(int widestScoreWidth) {
        return Math.max(MIN_RADAR_CELL, Math.max(0, widestScoreWidth) + RADAR_TEXT_PADDING);
    }

    public static int labelWidth(int labelX, int labelRight) {
        return Math.max(1, labelRight - labelX);
    }

    /**
     * Resolves the dense overlay metrics without touching Minecraft classes. On a sufficiently wide
     * viewport every category is kept in one row; narrower viewports retain a readable minimum
     * width and naturally wrap into additional rows.
     */
    public static ClickGuiMetrics clickGuiMetrics(
        int screenWidth,
        int categoryCount,
        int scalePercent,
        int densityPercent
    ) {
        int safeWidth = Math.max(1, screenWidth);
        int safeCount = Math.max(1, categoryCount);
        int scale = Math.clamp(scalePercent, 70, 140);
        int density = Math.clamp(densityPercent, 70, 160);
        int gap = Math.clamp(Math.round(6.0f * scale / 100.0f), 5, 10);
        int maxColumns = Math.clamp(
            (safeWidth - 8 + gap) / (CLICK_GUI_MIN_WINDOW_WIDTH + gap),
            1,
            safeCount
        );
        int rowCount = (safeCount + maxColumns - 1) / maxColumns;
        int balancedColumns = (safeCount + rowCount - 1) / rowCount;
        int balancedFitWidth = (safeWidth - 8 - gap * (balancedColumns - 1)) / balancedColumns;
        int windowWidth = Math.clamp(
            balancedFitWidth,
            CLICK_GUI_MIN_WINDOW_WIDTH,
            CLICK_GUI_MAX_WINDOW_WIDTH
        );

        float verticalScale = scale / 100.0f * 100.0f / density;
        int moduleHeight = Math.clamp(Math.round(18.0f * verticalScale), 15, 26);
        int headerHeight = Math.clamp(Math.round(24.0f * scale / 100.0f * 100.0f / Math.max(90, density)), 20, 30);
        int cornerRadius = 5;
        return new ClickGuiMetrics(windowWidth, headerHeight, moduleHeight, gap, cornerRadius);
    }

    public static int clickGuiColumns(int screenWidth, int windowWidth, int gap, int categoryCount) {
        int count = Math.max(1, categoryCount);
        int columns = (Math.max(1, screenWidth) - 4 + Math.max(0, gap))
            / (Math.max(1, windowWidth) + Math.max(0, gap));
        return Math.clamp(columns, 1, count);
    }

    public static int percentAlpha(int percent) {
        return Math.clamp(Math.round(255.0f * Math.clamp(percent, 0, 100) / 100.0f), 0, 255);
    }

    public record ClickGuiMetrics(
        int windowWidth,
        int headerHeight,
        int moduleHeight,
        int gap,
        int cornerRadius
    ) {
    }
}
