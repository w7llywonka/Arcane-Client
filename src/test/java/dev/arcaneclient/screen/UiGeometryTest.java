package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiGeometryTest {
    @Test
    void radarCellAlwaysLeavesPaddingAroundTwoDigitScores() {
        for (int scoreWidth = 0; scoreWidth <= 30; scoreWidth++) {
            int cellSize = UiGeometry.radarCellSize(scoreWidth);
            assertTrue(cellSize >= 15);
            assertTrue(cellSize - scoreWidth >= 4);
        }
    }

    @Test
    void moduleLabelsNeverReceiveNegativeDrawingSpace() {
        assertEquals(43, UiGeometry.labelWidth(14, 57));
        assertEquals(1, UiGeometry.labelWidth(57, 40));
    }

    @Test
    void clickGuiKeepsReadableWindowsAcrossACommonWideViewport() {
        UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(960, 8, 100, 100);
        assertEquals(8, UiGeometry.clickGuiColumns(960, metrics.windowWidth(), metrics.gap(), 8));
        assertTrue(metrics.windowWidth() >= 86);
        assertEquals(18, metrics.moduleHeight());
        assertEquals(21, metrics.headerHeight());
        assertEquals(7, metrics.gap());
    }

    @Test
    void clickGuiWrapsInsteadOfCrushingWindowsOnNarrowScreens() {
        UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(480, 8, 100, 100);
        int columns = UiGeometry.clickGuiColumns(480, metrics.windowWidth(), metrics.gap(), 8);
        assertTrue(columns > 1 && columns < 8);
        assertTrue(metrics.windowWidth() >= 86);
    }

    @Test
    void sevenCategoriesUseReadableFourPlusThreeAtTheGameTestGuiScale() {
        UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(640, 7, 100, 100);
        assertEquals(4, UiGeometry.clickGuiColumns(640, metrics.windowWidth(), metrics.gap(), 7));
        assertTrue(metrics.windowWidth() >= 112);
    }

    @Test
    void actualThreeXGameScaleUsesABalancedFourByThreeGrid() {
        UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(427, 7, 100, 100);
        int columns = UiGeometry.clickGuiColumns(427, metrics.windowWidth(), metrics.gap(), 7);
        assertEquals(3, columns);
        assertEquals(1, 7 - 2 * columns);
    }

    @Test
    void wrappedLayoutsAvoidLeavingOneCategoryAlone() {
        UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(640, 8, 100, 100);
        assertEquals(4, UiGeometry.clickGuiColumns(640, metrics.windowWidth(), metrics.gap(), 8));
    }

    @Test
    void allSevenCategoriesFitAcrossTheTopAtDesktopSize() {
        for (int width : new int[] {800, 854, 960, 1280}) {
            UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(width, 7, 100, 100);
            assertEquals(7, UiGeometry.clickGuiColumns(width, metrics.windowWidth(), metrics.gap(), 7));
            assertTrue(7 * metrics.windowWidth() + 6 * metrics.gap() <= width);
        }
    }

    @Test
    void densityAndOpacityControlsRemainBounded() {
        UiGeometry.ClickGuiMetrics relaxed = UiGeometry.clickGuiMetrics(960, 8, 100, 70);
        UiGeometry.ClickGuiMetrics dense = UiGeometry.clickGuiMetrics(960, 8, 100, 160);
        assertTrue(dense.moduleHeight() < relaxed.moduleHeight());
        assertEquals(0, UiGeometry.percentAlpha(-50));
        assertEquals(255, UiGeometry.percentAlpha(150));
        assertEquals(209, UiGeometry.percentAlpha(82));
    }

    @Test
    void largeMinecraftGuiScaleStillLeavesOneCompleteModuleRow() {
        UiGeometry.ClickGuiMetrics base = UiGeometry.clickGuiMetrics(427, 7, 100, 100);
        UiGeometry.ClickGuiMetrics fitted = UiGeometry.fitClickGuiHeight(base, 427, 240 - 34 - 8 - 20 - 4, 7);
        int workHeight = 240 - 34 - 8 - 20 - 4;
        int rows = (7 + UiGeometry.clickGuiColumns(427, base.windowWidth(), base.gap(), 7) - 1)
            / UiGeometry.clickGuiColumns(427, base.windowWidth(), base.gap(), 7);
        int band = (workHeight - (rows - 1) * base.gap()) / rows;
        assertTrue(fitted.headerHeight() + fitted.moduleHeight() + 6 + 4 <= band);
        assertEquals(18, fitted.moduleHeight());
        assertEquals(base, UiGeometry.fitClickGuiHeight(base, 427, 500, 7));
    }

    @Test
    void glassOverlayKeepsReadableGapsAcrossScalingAndDensitySettings() {
        for (int width : new int[] {320, 427, 480, 640, 800, 960, 1280}) {
            for (int count : new int[] {1, 6, 7, 8}) {
                for (int scale : new int[] {70, 100, 140}) {
                    for (int density : new int[] {70, 100, 160}) {
                        UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(width, count, scale, density);
                        int columns = UiGeometry.clickGuiColumns(width, metrics.windowWidth(), metrics.gap(), count);
                        assertTrue(columns >= 1 && columns <= count);
                        assertTrue(metrics.windowWidth() >= 104 && metrics.windowWidth() <= 158);
                        assertTrue(metrics.moduleHeight() >= 15 && metrics.moduleHeight() <= 25);
                        assertTrue(metrics.headerHeight() >= 19 && metrics.headerHeight() <= 28);
                        assertTrue(metrics.gap() >= 5 && metrics.gap() <= 11);
                        assertTrue(columns * metrics.windowWidth() + (columns - 1) * metrics.gap() <= width,
                            "Panels must fit without overlap at width=" + width + ", scale=" + scale + ", density=" + density);
                    }
                }
            }
        }
    }
}
