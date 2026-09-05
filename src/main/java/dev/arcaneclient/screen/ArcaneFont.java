package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

/** Picks the bundled Barlow Condensed font for compact interface panels. */
@Environment(EnvType.CLIENT)
public final class ArcaneFont {
    private static final int MAX_OVERSAMPLE = 6;
    private static final Identifier[] SCALED_FONT_IDS = new Identifier[MAX_OVERSAMPLE];

    static {
        for (int oversample = 1; oversample <= MAX_OVERSAMPLE; oversample++) {
            SCALED_FONT_IDS[oversample - 1] = ArcaneClient.id("ui_x" + oversample);
        }
    }

    private static Identifier styledId;
    private static Style cachedStyle;

    private ArcaneFont() {
    }

    public static TextRenderer renderer(MinecraftClient client) {
        return client.textRenderer;
    }

    public static Text text(String value) {
        return Text.literal(value).setStyle(style());
    }

    public static int width(TextRenderer renderer, String value) {
        return renderer.getWidth(text(value));
    }

    public static OrderedText trimmed(TextRenderer renderer, String value, int maxWidth) {
        if (width(renderer, value) <= maxWidth) return text(value).asOrderedText();
        int available = Math.max(0, maxWidth - width(renderer, "…"));
        String prefix = renderer.trimToWidth(text(value), available).getString();
        return text(prefix + "…").asOrderedText();
    }

    public static Style style() {
        Identifier id = fontId();
        if (!id.equals(styledId) || cachedStyle == null) {
            styledId = id;
            cachedStyle = Style.EMPTY.withFont(new StyleSpriteSource.Font(id));
        }
        return cachedStyle;
    }

    /** Re-selects the scale-matched bundled font after a window or resource change. */
    public static void invalidate() {
        cachedStyle = null;
        styledId = null;
    }

    private static Identifier fontId() {
        MinecraftClient client = MinecraftClient.getInstance();
        int scale = Math.clamp(client.getWindow().getScaleFactor(), 1, MAX_OVERSAMPLE);
        return SCALED_FONT_IDS[scale - 1];
    }
}
