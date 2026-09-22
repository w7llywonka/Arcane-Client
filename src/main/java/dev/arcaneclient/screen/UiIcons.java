package dev.arcaneclient.screen;

import dev.arcaneclient.screen.vector.VectorUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small original line symbols for Arcane's category headers. */
final class UiIcons {
    private UiIcons() {}
    static void category(GuiGraphicsExtractor context, String category, int x, int y, int color) {
        if (!VectorUi.recording()) {
            RoundedGui.outlineOnly(context, x + 1, y + 1, 7, 7, 2, color);
            return;
        }
        switch (category) {
            case "BASE FINDING" -> {
                VectorUi.outline(x, y, 9, 9, 4.5f, 0.65f, color);
                line(x + 4.5f, y - 1, x + 4.5f, y + 10, color);
                line(x - 1, y + 4.5f, x + 10, y + 4.5f, color);
            }
            case "COMBAT" -> {
                line(x + 1, y, x + 9, y + 8, color); line(x + 8, y, x, y + 8, color);
                line(x, y + 5, x + 4, y + 9, color); line(x + 5, y + 9, x + 9, y + 5, color);
            }
            case "MOVEMENT" -> {
                line(x, y + 2, x + 7, y + 2, color); line(x + 5, y, x + 7, y + 2, color);
                line(x + 7, y + 2, x + 5, y + 4, color); line(x + 2, y + 7, x + 9, y + 7, color);
                line(x + 2, y + 7, x + 4, y + 5, color); line(x + 2, y + 7, x + 4, y + 9, color);
            }
            case "ESP" -> {
                line(x, y + 4, x + 4, y + 1, color); line(x + 4, y + 1, x + 9, y + 4, color);
                line(x, y + 4, x + 4, y + 7, color); line(x + 4, y + 7, x + 9, y + 4, color);
                VectorUi.rect(x + 3, y + 3, 3, 3, 1.5f, color);
            }
            case "RENDER" -> {
                VectorUi.outline(x + 2, y + 2, 5, 5, 2.5f, 0.65f, color);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    line(x + 4.5f + (float)Math.cos(a) * 3.7f, y + 4.5f + (float)Math.sin(a) * 3.7f,
                         x + 4.5f + (float)Math.cos(a) * 5, y + 4.5f + (float)Math.sin(a) * 5, color);
                }
            }
            case "UTILITY" -> {
                line(x + 1, y + 8, x + 7, y + 2, color); line(x + 5, y, x + 5, y + 3, color);
                line(x + 5, y + 3, x + 8, y + 3, color); line(x + 8, y + 3, x + 9, y, color);
            }
            default -> {
                for (int yy = 0; yy <= 5; yy += 5) for (int xx = 0; xx <= 5; xx += 5)
                    VectorUi.outline(x + xx, y + yy, 3.5f, 3.5f, 1, 0.65f, color);
            }
        }
    }
    private static void line(float x1, float y1, float x2, float y2, int color) {
        VectorUi.line(x1, y1, x2, y2, 0.7f, color);
    }
}
