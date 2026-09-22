package dev.arcaneclient.screen;

import dev.arcaneclient.mixin.ArcaneTextFieldAccessor;
import dev.arcaneclient.screen.vector.VectorUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Arcane screen drawing with a native Minecraft fallback if vector support is unavailable. */
public final class UiDraw {
    public static final float FONT_SIZE = 7.25f;
    public static final float HEADER_FONT_SIZE = 8.25f;
    public static final float SETTING_FONT_SIZE = 6.25f;
    private UiDraw() {}

    public static void fill(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        if (VectorUi.recording()) VectorUi.rect(x1, y1, x2 - x1, y2 - y1, 0, color);
        else context.fill(x1, y1, x2, y2, color);
    }

    public static void text(GuiGraphicsExtractor context, Font font, Component value, int x, int y, int color, boolean shadow) {
        if (VectorUi.recording()) VectorUi.text(value.getString(), x, y, FONT_SIZE, color);
        else context.text(font, value, x, y, color, shadow);
    }

    public static void text(GuiGraphicsExtractor context, Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
        if (VectorUi.recording()) VectorUi.text(plain(value), x, y, FONT_SIZE, color);
        else context.text(font, value, x, y, color, shadow);
    }

    public static String plain(FormattedCharSequence value) {
        StringBuilder plain = new StringBuilder();
        value.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
        return plain.toString();
    }

    public static void textSized(GuiGraphicsExtractor context, Font font, Component value, int x, int y,
                                 float size, int color, boolean shadow) {
        if (VectorUi.recording()) VectorUi.text(value.getString(), x, y, size, color);
        else {
            context.pose().pushMatrix();
            context.pose().translate(x, y);
            context.pose().scale(size / FONT_SIZE, size / FONT_SIZE);
            context.text(font, value, 0, 0, color, shadow);
            context.pose().popMatrix();
        }
    }

    public static void textSized(GuiGraphicsExtractor context, Font font, FormattedCharSequence value, int x, int y,
                                 float size, int color, boolean shadow) {
        textSized(context, font, ArcaneFont.text(plain(value)), x, y, size, color, shadow);
    }

    public static int widthSized(Font font, String value, float size) {
        if (VectorUi.recording()) {
            float measured = VectorUi.textWidth(value, size);
            if (measured >= 0) return (int)Math.ceil(measured);
        }
        return (int)Math.ceil(font.width(ArcaneFont.text(value)) * size / FONT_SIZE);
    }

    public static void enableScissor(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2) {
        if (VectorUi.recording()) VectorUi.pushClip(x1, y1, x2 - x1, y2 - y1);
        else context.enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(GuiGraphicsExtractor context) {
        if (VectorUi.recording()) VectorUi.popClip();
        else context.disableScissor();
    }

    public static void field(GuiGraphicsExtractor context, Font font, EditBox input,
                             String placeholder, int mouseX, int mouseY, float delta, ClickGuiColors colors) {
        if (!input.isVisible()) return;
        if (!VectorUi.recording()) { input.extractRenderState(context, mouseX, mouseY, delta); return; }
        int x = input.getX(), y = input.getY(), width = input.getWidth();
        String value = input.getValue();
        if (value.isEmpty()) {
            VectorUi.text(placeholder, x, y, FONT_SIZE, colors.faint());
        }
        ArcaneTextFieldAccessor edit = (ArcaneTextFieldAccessor) input;
        int start = Math.clamp(edit.arcane$firstCharacterIndex(), 0, value.length());
        String visible = value.substring(start);
        int cursor = Math.clamp(input.getCursorPosition() - start, 0, visible.length());
        int end = Math.clamp(edit.arcane$selectionEnd() - start, 0, visible.length());
        float cursorX = x + VectorUi.textWidth(visible.substring(0, cursor), FONT_SIZE);
        float endX = x + VectorUi.textWidth(visible.substring(0, end), FONT_SIZE);
        VectorUi.pushClip(x, y - 1, width, Math.max(12, input.getHeight()));
        if (input.isFocused() && cursor != end) {
            VectorUi.rect(Math.min(cursorX, endX), y - 1, Math.abs(endX - cursorX), 11, 2,
                (colors.accent() & 0xFFFFFF) | 0x55000000);
        }
        VectorUi.text(visible, x, y, FONT_SIZE, colors.text());
        if (input.isFocused() && (System.nanoTime() / 500_000_000L) % 2 == 0) {
            VectorUi.line(cursorX, y, cursorX, y + 9, 0.7f, colors.accentBright());
        }
        VectorUi.popClip();
    }
}
