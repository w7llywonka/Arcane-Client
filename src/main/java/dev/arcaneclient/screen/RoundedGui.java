package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** High-density, linearly filtered rounded GUI surfaces. */
@Environment(EnvType.CLIENT)
public final class RoundedGui {
    static final int TEXTURE_SIZE = 68;
    static final int SOURCE_BORDER = 32;
    static final int SOURCE_CENTER = TEXTURE_SIZE - SOURCE_BORDER * 2;

    private static final Identifier FILL_TEXTURE = ArcaneClient.id("textures/gui/rounded_fill.png");
    private static final Identifier OUTLINE_TEXTURE = ArcaneClient.id("textures/gui/rounded_outline.png");

    private RoundedGui() {
    }

    public static void fill(DrawContext graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int safeRadius = effectiveRadius(width, height, radius);
        if (safeRadius == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        drawNineSlice(graphics, FILL_TEXTURE, x, y, width, height, safeRadius, color);
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
        if (width <= 0 || height <= 0) return;
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
        int safeRadius = effectiveRadius(width, height, radius);
        if (safeRadius == 0) {
            graphics.drawStrokedRectangle(x, y, width, height, color);
            return;
        }
        drawNineSlice(graphics, OUTLINE_TEXTURE, x, y, width, height, safeRadius, color);
    }

    static int effectiveRadius(int width, int height, int radius) {
        return Math.max(0, Math.min(radius, Math.min(width, height) / 2));
    }

    private static void drawNineSlice(
        DrawContext graphics,
        Identifier texture,
        int x,
        int y,
        int width,
        int height,
        int border,
        int color
    ) {
        int centerWidth = width - border * 2;
        int centerHeight = height - border * 2;
        int sourceRight = SOURCE_BORDER + SOURCE_CENTER;

        drawSlice(graphics, texture, x, y, 0, 0, border, border, SOURCE_BORDER, SOURCE_BORDER, color);
        drawSlice(graphics, texture, x + border, y, SOURCE_BORDER, 0, centerWidth, border, SOURCE_CENTER, SOURCE_BORDER, color);
        drawSlice(graphics, texture, x + width - border, y, sourceRight, 0, border, border, SOURCE_BORDER, SOURCE_BORDER, color);

        drawSlice(graphics, texture, x, y + border, 0, SOURCE_BORDER, border, centerHeight, SOURCE_BORDER, SOURCE_CENTER, color);
        drawSlice(graphics, texture, x + border, y + border, SOURCE_BORDER, SOURCE_BORDER, centerWidth, centerHeight, SOURCE_CENTER, SOURCE_CENTER, color);
        drawSlice(graphics, texture, x + width - border, y + border, sourceRight, SOURCE_BORDER, border, centerHeight, SOURCE_BORDER, SOURCE_CENTER, color);

        drawSlice(graphics, texture, x, y + height - border, 0, sourceRight, border, border, SOURCE_BORDER, SOURCE_BORDER, color);
        drawSlice(graphics, texture, x + border, y + height - border, SOURCE_BORDER, sourceRight, centerWidth, border, SOURCE_CENTER, SOURCE_BORDER, color);
        drawSlice(graphics, texture, x + width - border, y + height - border, sourceRight, sourceRight, border, border, SOURCE_BORDER, SOURCE_BORDER, color);
    }

    private static void drawSlice(
        DrawContext graphics,
        Identifier texture,
        int x,
        int y,
        int u,
        int v,
        int width,
        int height,
        int sourceWidth,
        int sourceHeight,
        int color
    ) {
        if (width <= 0 || height <= 0) return;
        graphics.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            u,
            v,
            width,
            height,
            sourceWidth,
            sourceHeight,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            color
        );
    }
}