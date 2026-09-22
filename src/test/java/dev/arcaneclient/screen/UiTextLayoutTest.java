package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiTextLayoutTest {
    @Test
    void compactCycleLeavesRoomForItsLabelGapAndForwardArrow() {
        assertEquals(41, UiTextLayout.valueWidthBudget(104, 30, 8));
        assertEquals(53, UiTextLayout.valueWidthBudget(104, 26, 0));
    }

    @Test
    void longValuesNeverConsumeTheReservedLabelArea() {
        for (int rowWidth = 104; rowWidth <= 158; rowWidth++) {
            for (int labelWidth : new int[] {0, 20, 30, 42, 80, 200}) {
                for (int adornmentWidth : new int[] {0, 8, 12}) {
                    int budget = UiTextLayout.valueWidthBudget(rowWidth, labelWidth, adornmentWidth);
                    assertTrue(budget >= 0);
                    assertTrue(budget + Math.min(labelWidth, 42) + 4 + adornmentWidth <= rowWidth - 21);
                }
            }
        }
    }

    @Test
    void narrowLabelsDisappearInsteadOfDrawingAnOverlappingEllipsis() {
        assertFalse(UiTextLayout.fitsEllipsis(0, 6));
        assertFalse(UiTextLayout.fitsEllipsis(1, 6));
        assertFalse(UiTextLayout.fitsEllipsis(5, 6));
        assertTrue(UiTextLayout.fitsEllipsis(6, 6));
        assertTrue(UiTextLayout.fitsEllipsis(7, 6));
        assertEquals(0, UiTextLayout.valueWidthBudget(12, 42, 8));
    }
}
