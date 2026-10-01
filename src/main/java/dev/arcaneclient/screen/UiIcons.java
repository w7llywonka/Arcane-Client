package dev.arcaneclient.screen;

import dev.arcaneclient.screen.vector.VectorUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small original line symbols for Arcane's category headers. */
final class UiIcons {
    private UiIcons() {}
    static void category(GuiGraphicsExtractor context, String category, int x, int y, int color) {
        switch (category) {
            case "BASE FINDING" -> {
                outline(context, x, y, 9, 9, 4.5f, color);
                line(context, x + 4.5f, y - 1, x + 4.5f, y + 10, color);
                line(context, x - 1, y + 4.5f, x + 10, y + 4.5f, color);
            }
            case "COMBAT" -> {
                line(context, x + 1, y, x + 9, y + 8, color); line(context, x + 8, y, x, y + 8, color);
                line(context, x, y + 5, x + 4, y + 9, color); line(context, x + 5, y + 9, x + 9, y + 5, color);
            }
            case "MOVEMENT" -> {
                line(context, x, y + 2, x + 7, y + 2, color); line(context, x + 5, y, x + 7, y + 2, color);
                line(context, x + 7, y + 2, x + 5, y + 4, color); line(context, x + 2, y + 7, x + 9, y + 7, color);
                line(context, x + 2, y + 7, x + 4, y + 5, color); line(context, x + 2, y + 7, x + 4, y + 9, color);
            }
            case "ESP" -> {
                line(context, x, y + 4, x + 4, y + 1, color); line(context, x + 4, y + 1, x + 9, y + 4, color);
                line(context, x, y + 4, x + 4, y + 7, color); line(context, x + 4, y + 7, x + 9, y + 4, color);
                rect(context, x + 3, y + 3, 3, 3, 1.5f, color);
            }
            case "RENDER" -> {
                outline(context, x + 2, y + 2, 5, 5, 2.5f, color);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    line(context, x + 4.5f + (float)Math.cos(a) * 3.7f, y + 4.5f + (float)Math.sin(a) * 3.7f,
                         x + 4.5f + (float)Math.cos(a) * 5, y + 4.5f + (float)Math.sin(a) * 5, color);
                }
            }
            case "UTILITY" -> {
                line(context, x + 1, y + 8, x + 7, y + 2, color); line(context, x + 5, y, x + 5, y + 3, color);
                line(context, x + 5, y + 3, x + 8, y + 3, color); line(context, x + 8, y + 3, x + 9, y, color);
            }
            default -> {
                for (int yy = 0; yy <= 5; yy += 5) for (int xx = 0; xx <= 5; xx += 5)
                    outline(context, x + xx, y + yy, 3.5f, 3.5f, 1, color);
            }
        }
    }

    private static void line(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color) {
        if (VectorUi.recording()) {
            VectorUi.line(x1, y1, x2, y2, 0.7f, color);
            return;
        }
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float)Math.hypot(dx, dy);
        if (length <= 0) return;
        int segments = Math.max(2, (int)Math.ceil(length / 0.35f));
        var pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(x1, y1);
            pose.rotate((float)Math.atan2(dy, dx));
            pose.translate(0, -0.35f);
            pose.scale(length / segments, 0.35f);
            RoundedGui.fill(context, 0, 0, segments, 2, 1, color);
        } finally {
            pose.popMatrix();
        }
    }

    private static void outline(GuiGraphicsExtractor context, float x, float y, float width, float height,
                                float radius, int color) {
        if (VectorUi.recording()) {
            VectorUi.outline(x, y, width, height, radius, 0.65f, color);
            return;
        }
        var pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(0.5f, 0.5f);
            // Two native units per pixel retain the half-pixel dimensions of the vector symbols.
            RoundedGui.outlineOnly(context, 0, 0, Math.round(width * 2), Math.round(height * 2),
                Math.round(radius * 2), color);
        } finally {
            pose.popMatrix();
        }
    }

    private static void rect(GuiGraphicsExtractor context, float x, float y, float width, float height,
                             float radius, int color) {
        if (VectorUi.recording()) {
            VectorUi.rect(x, y, width, height, radius, color);
            return;
        }
        var pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(0.5f, 0.5f);
            RoundedGui.fill(context, 0, 0, Math.round(width * 2), Math.round(height * 2),
                Math.round(radius * 2), color);
        } finally {
            pose.popMatrix();
        }
    }
}
