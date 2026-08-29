package dev.arcaneclient.screen;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;

/**
 * Seven-by-seven category glyphs stored as row bit masks. Drawing the header art from masks keeps
 * it crisp at every GUI scale and costs no texture, atlas slot, or resource lookup.
 */
@Environment(EnvType.CLIENT)
public final class GuiIcons {
    public static final int SIZE = 7;

    private static final int[] SWORD = glyph(
        ".....##",
        "....##.",
        "...##..",
        "..##...",
        "#####..",
        "..##...",
        ".##...."
    );
    private static final int[] EYE = glyph(
        ".......",
        "..###..",
        ".#...#.",
        "#..#..#",
        ".#...#.",
        "..###..",
        "......."
    );
    private static final int[] VIEWPORT = glyph(
        ".......",
        ".#####.",
        ".#...#.",
        ".#.#.#.",
        ".#...#.",
        ".#####.",
        "......."
    );
    private static final int[] SPARK = glyph(
        "...#...",
        "..###..",
        ".#####.",
        "#######",
        ".#####.",
        "..###..",
        "...#..."
    );
    private static final int[] SLIDERS = glyph(
        ".......",
        "..#....",
        "#######",
        ".......",
        ".....#.",
        "#######",
        "......."
    );
    private static final int[] TARGET = glyph(
        "...#...",
        ".#####.",
        ".#...#.",
        "##.#.##",
        ".#...#.",
        ".#####.",
        "...#..."
    );

    private static final int[] MAGNIFIER = glyph(
        ".###...",
        "#...#..",
        "#...#..",
        "#...#..",
        ".###...",
        "....##.",
        ".....##"
    );
    private static final int[] CROSS = glyph(
        ".......",
        ".#...#.",
        "..#.#..",
        "...#...",
        "..#.#..",
        ".#...#.",
        "......."
    );

    private GuiIcons() {
    }

    /** The glyph that marks the search field. */
    public static int[] search() {
        return MAGNIFIER;
    }

    /** The glyph that clears the search field. */
    public static int[] close() {
        return CROSS;
    }

    /** Falls back to the spark so a category added later still draws a glyph. */
    public static int[] forCategory(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "COMBAT" -> SWORD;
            case "ESP" -> EYE;
            case "RENDER" -> VIEWPORT;
            case "UTILITY" -> SLIDERS;
            case "BASE FINDING" -> TARGET;
            default -> SPARK;
        };
    }

    /** Draws one glyph, merging each row's set bits into as few rectangles as the shape allows. */
    public static void draw(DrawContext graphics, int[] glyph, int x, int y, int color) {
        for (int row = 0; row < glyph.length; row++) {
            int mask = glyph[row];
            int column = 0;
            while (column < SIZE) {
                if ((mask >> SIZE - 1 - column & 1) == 0) {
                    column++;
                    continue;
                }
                int run = column;
                while (run < SIZE && (mask >> SIZE - 1 - run & 1) == 1) {
                    run++;
                }
                graphics.fill(x + column, y + row, x + run, y + row + 1, color);
                column = run;
            }
        }
    }

    static int[] glyph(String... rows) {
        int[] masks = new int[rows.length];
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            if (line.length() != SIZE) {
                throw new IllegalArgumentException("Glyph rows must be " + SIZE + " columns wide: " + line);
            }
            int mask = 0;
            for (int column = 0; column < SIZE; column++) {
                if (line.charAt(column) == '#') {
                    mask |= 1 << SIZE - 1 - column;
                }
            }
            masks[row] = mask;
        }
        return masks;
    }
}
