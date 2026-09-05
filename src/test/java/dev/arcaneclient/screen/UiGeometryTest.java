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
        assertEquals(24, metrics.headerHeight());
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
    }
}
