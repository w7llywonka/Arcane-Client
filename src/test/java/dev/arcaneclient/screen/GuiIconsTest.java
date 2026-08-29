package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GuiIconsTest {
    @Test
    void everyCategoryResolvesToASquareGlyphThatFitsItsCell() {
        for (String name : new String[]{"COMBAT", "ESP", "RENDER", "CLIENT", "UTILITY", "BASE FINDING", "SOMETHING NEW"}) {
            int[] glyph = GuiIcons.forCategory(name);
            assertEquals(GuiIcons.SIZE, glyph.length, name);
            int lit = 0;
            for (int row : glyph) {
                assertTrue(row >= 0 && row < 1 << GuiIcons.SIZE, name + " row escapes its cell");
                lit += Integer.bitCount(row);
            }
            assertTrue(lit > 0, name + " must draw something");
        }
        assertSame(GuiIcons.forCategory("combat"), GuiIcons.forCategory("COMBAT"));
    }

    @Test
    void maskBitsRunLeftToRight() {
        int[] glyph = GuiIcons.glyph("#......", ".....##", "#######");
        assertEquals(0b1000000, glyph[0]);
        assertEquals(0b0000011, glyph[1]);
        assertEquals(0b1111111, glyph[2]);
    }

    @Test
    void aMisalignedGlyphIsRejectedAtLoadRatherThanDrawnWrong() {
        assertThrows(IllegalArgumentException.class, () -> GuiIcons.glyph("###"));
    }
}
