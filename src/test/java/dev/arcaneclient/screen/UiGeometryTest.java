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
}
