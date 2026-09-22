package dev.arcaneclient.screen;

import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;

import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.vector.VectorUi;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** A small independent theme editor; its controls never change module or scanner settings. */
public final class ThemePanel {
    private static final int WIDTH = 105;
    private static final int HEIGHT = 238;
    private static final int HEADER = 19;
    private static final int ROW = 13;
    private static final int CUSTOM_Y = HEADER + ROW * 6;
    private static final int FIELD_X = 10, FIELD_Y = 112, FIELD_W = 85, FIELD_H = 40;
    private static final int HUE_Y = 155, HUE_H = 6;
    private static final int OPACITY_Y = 188;
    private static final int BLUR_Y = 196, MOTION_Y = 208, FONT_Y = 220;
    private static final float FONT_SIZE = 7.25f;

    private final ArcaneConfig config;
    private int x, y;
    private boolean open;
    private boolean placed;
    private int screenWidth = 640, screenHeight = 360;
    private int dragOffsetX, dragOffsetY;
    private Drag drag = Drag.NONE;
    private float hue, saturation, brightness;
    private int knownAccent;

    public ThemePanel(ArcaneConfig config) {
        this.config = config;
        this.open = config.uiThemesOpen;
        this.x = config.uiThemesX;
        this.y = config.uiThemesY;
        this.placed = this.x >= 0 && this.y >= 0;
        syncAccent();
    }

    public int x() { return this.x; }
    public int y() { return this.y; }
    public boolean isOpen() { return this.open; }
    public void setOpen(boolean value) {
        this.open = value;
        this.config.uiThemesOpen = value;
        if (!value) this.drag = Drag.NONE;
    }
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        this.placed = true;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenW, int screenH) {
        this.screenWidth = screenW;
        this.screenHeight = screenH;
        if (!this.placed) {
            this.x = screenW - WIDTH - 9;
            this.y = screenH - HEIGHT - 9;
            this.placed = true;
        }
        clamp();
        if (!this.open) return;
        if (this.knownAccent != this.config.uiAccentColor) syncAccent();
        ClickGuiColors colors = ClickGuiColors.display(this.config);
        RoundedGui.fill(graphics, this.x + 2, this.y + 3, WIDTH, HEIGHT, 6, 0x42000000);
        RoundedGui.fill(graphics, this.x, this.y, WIDTH, HEIGHT, 6, colors.window());
        RoundedGui.outlineOnly(graphics, this.x, this.y, WIDTH, HEIGHT, 6, colors.outlineSoft());
        text(graphics, "Themes", 9, 6, colors.text());
        text(graphics, "×", WIDTH - 13, 5, colors.muted());
        UiDraw.fill(graphics, this.x + 8, this.y + HEADER - 1, this.x + WIDTH - 8, this.y + HEADER, colors.accentDim());

        for (int i = 0; i < 6; i++) {
            ClickGuiTheme preset = ClickGuiTheme.fromConfig(i);
            int rowY = HEADER + i * ROW;
            if (hit(mouseX, mouseY, 4, rowY, WIDTH - 8, ROW))
                RoundedGui.fill(graphics, this.x + 4, this.y + rowY, WIDTH - 8, ROW, 3, colors.hover());
            radio(graphics, 10, rowY + 4, !this.config.customUiColors && this.config.uiTheme == i, preset.accent());
            String name = preset.label().substring(0, 1) + preset.label().substring(1).toLowerCase(Locale.ROOT);
            text(graphics, name, 22, rowY + 3, colors.muted());
            RoundedGui.fill(graphics, this.x + WIDTH - 17, this.y + rowY + 4, 6, 6, 3, preset.accent());
        }
        radio(graphics, 10, CUSTOM_Y + 4, this.config.customUiColors, this.config.uiAccentColor);
        text(graphics, "Custom", 22, CUSTOM_Y + 3, colors.muted());
        drawColorField(graphics);
        text(graphics, String.format(Locale.ROOT, "#%06X", this.config.uiAccentColor & 0xFFFFFF), 10, 165, colors.text());
        RoundedGui.fill(graphics, this.x + WIDTH - 20, this.y + 164, 10, 9, 2, this.config.uiAccentColor);
        text(graphics, "Opacity", 10, 177, colors.muted());
        text(graphics, this.config.uiOpacityPercent + "%", 72, 177, colors.text());
        RoundedGui.fill(graphics, this.x + FIELD_X, this.y + OPACITY_Y, FIELD_W, 3, 1, colors.outlineSoft());
        int filled = Math.round(FIELD_W * (Math.clamp(this.config.uiOpacityPercent, 45, 100) - 45) / 55.0f);
        RoundedGui.fill(graphics, this.x + FIELD_X, this.y + OPACITY_Y, filled, 3, 1, colors.accent());
        RoundedGui.fill(graphics, this.x + FIELD_X + Math.clamp(filled - 2, 0, FIELD_W - 5), this.y + OPACITY_Y - 1, 5, 5, 2, colors.text());
        toggle(graphics, "World blur", BLUR_Y, this.config.uiBlur, colors);
        toggle(graphics, "Reduced motion", MOTION_Y, this.config.uiReducedMotion, colors);
        text(graphics, "Font", 10, FONT_Y + 2, colors.muted());
        text(graphics, this.config.uiFont == 0 ? "Xuong >" : "Sora >", 61, FONT_Y + 2, colors.text());
    }

    public boolean contains(int mouseX, int mouseY) {
        return this.open && hit(mouseX, mouseY, 0, 0, WIDTH, HEIGHT);
    }

    public boolean mouseClicked(MouseButtonEvent click) {
        int mouseX = (int) click.x(), mouseY = (int) click.y();
        if (!contains(mouseX, mouseY)) return false;
        if (click.button() != SDL_BUTTON_LEFT) return true;
        if (hit(mouseX, mouseY, WIDTH - 19, 0, 19, HEADER)) {
            setOpen(false);
        } else if (hit(mouseX, mouseY, 0, 0, WIDTH - 19, HEADER)) {
            this.drag = Drag.PANEL;
            this.dragOffsetX = mouseX - this.x;
            this.dragOffsetY = mouseY - this.y;
        } else if (hit(mouseX, mouseY, 4, HEADER, WIDTH - 8, ROW * 6)) {
            this.config.uiTheme = Math.clamp((mouseY - this.y - HEADER) / ROW, 0, 5);
            this.config.customUiColors = false;
        } else if (hit(mouseX, mouseY, 4, CUSTOM_Y, WIDTH - 8, ROW)) {
            this.config.customUiColors = !this.config.customUiColors;
        } else if (hit(mouseX, mouseY, FIELD_X, FIELD_Y, FIELD_W, FIELD_H)) {
            this.drag = Drag.COLOR;
            updateDrag(mouseX, mouseY);
        } else if (hit(mouseX, mouseY, FIELD_X, HUE_Y - 2, FIELD_W, HUE_H + 4)) {
            this.drag = Drag.HUE;
            updateDrag(mouseX, mouseY);
        } else if (hit(mouseX, mouseY, FIELD_X - 2, OPACITY_Y - 4, FIELD_W + 4, 11)) {
            this.drag = Drag.OPACITY;
            updateDrag(mouseX, mouseY);
        } else if (hit(mouseX, mouseY, 4, BLUR_Y, WIDTH - 8, 12)) {
            this.config.uiBlur = !this.config.uiBlur;
        } else if (hit(mouseX, mouseY, 4, MOTION_Y, WIDTH - 8, 12)) {
            this.config.uiReducedMotion = !this.config.uiReducedMotion;
        } else if (hit(mouseX, mouseY, 4, FONT_Y, WIDTH - 8, 12)) {
            this.config.uiFont = this.config.uiFont == 0 ? 1 : 0;
            ArcaneFont.invalidate();
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.open || this.drag == Drag.NONE) return false;
        updateDrag((int) click.x(), (int) click.y());
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent click) {
        boolean handled = this.drag != Drag.NONE || contains((int) click.x(), (int) click.y());
        this.drag = Drag.NONE;
        this.config.uiThemesX = this.x;
        this.config.uiThemesY = this.y;
        this.config.uiThemesOpen = this.open;
        return handled;
    }

    private void updateDrag(int mouseX, int mouseY) {
        switch (this.drag) {
            case PANEL -> {
                this.x = mouseX - this.dragOffsetX;
                this.y = mouseY - this.dragOffsetY;
                clamp();
            }
            case COLOR -> {
                this.saturation = Math.clamp((mouseX - this.x - FIELD_X) / (float) (FIELD_W - 1), 0, 1);
                this.brightness = 1 - Math.clamp((mouseY - this.y - FIELD_Y) / (float) (FIELD_H - 1), 0, 1);
                applyAccent();
            }
            case HUE -> {
                this.hue = Math.clamp((mouseX - this.x - FIELD_X) / (float) (FIELD_W - 1), 0, 1);
                applyAccent();
            }
            case OPACITY -> this.config.uiOpacityPercent = 45 + Math.round(55 * Math.clamp((mouseX - this.x - FIELD_X) / (float) FIELD_W, 0, 1));
            case NONE -> {}
        }
    }

    private void drawColorField(GuiGraphicsExtractor graphics) {
        for (int column = 0; column < FIELD_W; column += 2) {
            int color = Color.HSBtoRGB(this.hue, column / (float) (FIELD_W - 1), 1);
            UiDraw.fill(graphics, this.x + FIELD_X + column, this.y + FIELD_Y,
                this.x + FIELD_X + Math.min(FIELD_W, column + 2), this.y + FIELD_Y + FIELD_H, color);
        }
        if (VectorUi.recording()) {
            VectorUi.gradient(this.x + FIELD_X, this.y + FIELD_Y, FIELD_W, FIELD_H, 0, 0x00000000, 0xFF000000);
        } else {
            for (int row = 0; row < FIELD_H; row++) {
                int alpha = Math.round(255 * row / (float) (FIELD_H - 1));
                UiDraw.fill(graphics, this.x + FIELD_X, this.y + FIELD_Y + row,
                    this.x + FIELD_X + FIELD_W, this.y + FIELD_Y + row + 1, alpha << 24);
            }
        }
        int markerX = this.x + FIELD_X + Math.round(this.saturation * (FIELD_W - 1));
        int markerY = this.y + FIELD_Y + Math.round((1 - this.brightness) * (FIELD_H - 1));
        RoundedGui.outlineOnly(graphics, markerX - 2, markerY - 2, 5, 5, 2, 0xFFFFFFFF);
        for (int column = 0; column < FIELD_W; column += 3) {
            UiDraw.fill(graphics, this.x + FIELD_X + column, this.y + HUE_Y,
                this.x + FIELD_X + Math.min(FIELD_W, column + 3), this.y + HUE_Y + HUE_H,
                Color.HSBtoRGB(column / (float) (FIELD_W - 1), 1, 1));
        }
        int hueX = this.x + FIELD_X + Math.round(this.hue * (FIELD_W - 1));
        RoundedGui.outlineOnly(graphics, hueX - 1, this.y + HUE_Y - 1, 3, HUE_H + 2, 1, 0xFFFFFFFF);
    }

    private void radio(GuiGraphicsExtractor graphics, int localX, int localY, boolean selected, int color) {
        RoundedGui.outlineOnly(graphics, this.x + localX, this.y + localY, 6, 6, 3, selected ? color : 0xFF72717C);
        if (selected) RoundedGui.fill(graphics, this.x + localX + 2, this.y + localY + 2, 2, 2, 1, color);
    }

    private void toggle(GuiGraphicsExtractor graphics, String label, int rowY, boolean selected, ClickGuiColors colors) {
        text(graphics, label, 10, rowY + 2, colors.muted());
        RoundedGui.fill(graphics, this.x + WIDTH - 17, this.y + rowY + 2, 7, 7, 2, selected ? colors.accent() : colors.hover());
        RoundedGui.outlineOnly(graphics, this.x + WIDTH - 17, this.y + rowY + 2, 7, 7, 2, colors.outline());
        if (selected) RoundedGui.fill(graphics, this.x + WIDTH - 15, this.y + rowY + 4, 3, 3, 1, colors.window() | 0xFF000000);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int localX, int localY, int color) {
        if (VectorUi.recording()) VectorUi.text(value, this.x + localX, this.y + localY, FONT_SIZE, color);
        else UiDraw.text(graphics, Minecraft.getInstance().font, ArcaneFont.text(value), this.x + localX, this.y + localY, color, false);
    }

    private boolean hit(int mouseX, int mouseY, int localX, int localY, int width, int height) {
        return mouseX >= this.x + localX && mouseX < this.x + localX + width
            && mouseY >= this.y + localY && mouseY < this.y + localY + height;
    }

    private void syncAccent() {
        this.knownAccent = this.config.uiAccentColor;
        float[] hsv = Color.RGBtoHSB((this.knownAccent >> 16) & 255, (this.knownAccent >> 8) & 255, this.knownAccent & 255, null);
        this.hue = hsv[0]; this.saturation = hsv[1]; this.brightness = hsv[2];
    }

    private void applyAccent() {
        this.config.customUiColors = true;
        this.config.uiAccentColor = Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
        this.knownAccent = this.config.uiAccentColor;
    }

    private void clamp() {
        this.x = Math.clamp(this.x, 2, Math.max(2, this.screenWidth - WIDTH - 2));
        this.y = Math.clamp(this.y, 2, Math.max(2, this.screenHeight - HEIGHT - 2));
    }

    private enum Drag { NONE, PANEL, COLOR, HUE, OPACITY }
}
