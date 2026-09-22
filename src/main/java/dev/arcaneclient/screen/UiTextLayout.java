package dev.arcaneclient.screen;

/** Width accounting for inline setting labels and values, independent of font rendering. */
final class UiTextLayout {
    private static final int SETTING_HORIZONTAL_PADDING = 21;
    private static final int LABEL_VALUE_GAP = 4;
    private static final int MAX_RESERVED_LABEL_WIDTH = 42;

    private UiTextLayout() {}

    static int valueWidthBudget(int rowWidth, int labelWidth, int adornmentWidth) {
        int contentWidth = Math.max(0, rowWidth - SETTING_HORIZONTAL_PADDING);
        int reservedLabel = Math.min(Math.max(0, labelWidth), Math.min(MAX_RESERVED_LABEL_WIDTH, contentWidth));
        return Math.max(0, contentWidth - reservedLabel - LABEL_VALUE_GAP - Math.max(0, adornmentWidth));
    }

    static boolean fitsEllipsis(int maxWidth, int ellipsisWidth) {
        return maxWidth > 0 && ellipsisWidth <= maxWidth;
    }
}
