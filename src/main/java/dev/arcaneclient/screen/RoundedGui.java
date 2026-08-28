package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public final class RoundedGui {
    private RoundedGui() {
    }

    public static void fill(DrawContext graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (safeRadius <= 1) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }

        graphics.fill(x, y + safeRadius, x + width, y + height - safeRadius, color);
        for (int row = 0; row < safeRadius; row++) {
            int inset = cornerInset(safeRadius, row);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    public static void outline(
        DrawContext graphics,
        int x,
        int y,
        int width,
        int height,
        int radius,
        int thickness,
        int color,
        int innerColor
    ) {
        fill(graphics, x, y, width, height, radius, color);
        int safeThickness = Math.max(1, Math.min(thickness, Math.max(1, Math.min(width, height) / 2)));
        fill(
            graphics,
            x + safeThickness,
            y + safeThickness,
            width - safeThickness * 2,
            height - safeThickness * 2,
            Math.max(0, radius - safeThickness),
            innerColor
        );
    }

    public static void outlineOnly(DrawContext graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int edgeRow = Math.min(row, height - row - 1);
            int inset = edgeRow < safeRadius ? cornerInset(safeRadius, edgeRow) : 0;
            if (row == 0 || row == height - 1) {
                graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            } else {
                graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
                graphics.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
            }
        }
    }

    static int cornerInset(int radius, int row) {
        double centerDistance = radius - row - 0.5;
        double horizontal = Math.sqrt(Math.max(0.0, radius * radius - centerDistance * centerDistance));
        return Math.max(0, radius - (int)Math.floor(horizontal));
    }
}