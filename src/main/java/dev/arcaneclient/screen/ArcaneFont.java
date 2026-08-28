package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.mixin.FontManagerAccessor;
import dev.arcaneclient.mixin.MinecraftClientAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

/**
 * Picks the font Arcane's interface draws with, and hands out text already styled to use it.
 *
 * <p><b>Why text is styled rather than drawn through a private renderer.</b> Minecraft resolves a
 * font from the {@link Style} on the text, through the {@code FontManager.Fonts} instance that backs
 * {@code client.textRenderer}. Constructing a private {@link TextRenderer} over one font storage is
 * the shorter route, and it is what Arcane used to do, but Caxton assumes {@code FontManager.Fonts}
 * is the only implementation of {@code TextRenderer.GlyphsProvider} and casts to it unconditionally
 * — so a private renderer makes Arcane silently lose its font whenever Caxton is installed. Styling
 * the text instead keeps one code path that works either way.
 *
 * <p><b>Which font.</b> With Caxton present, {@code ui_caxton} renders Inter as multi-channel signed
 * distance fields, which stay crisp at any size. Without it, Minecraft bakes a glyph once at
 * {@code size * oversample} pixels and samples the atlas with {@code FilterMode.NEAREST}, so a glyph
 * only looks right when its oversample matches the GUI scale; the {@code ui_xN} variants cover
 * scales 1 to 6 and the matching one is selected. Every variant shares Inter's metrics, so the
 * layout is identical whichever is chosen.
 */
@Environment(EnvType.CLIENT)
public final class ArcaneFont {
    private static final String CAXTON_MOD_ID = "caxton";
    private static final int MAX_OVERSAMPLE = 6;
    private static final Identifier CAXTON_FONT_ID = ArcaneClient.id("ui_caxton");
    private static final Identifier[] SCALED_FONT_IDS = new Identifier[MAX_OVERSAMPLE];

    static {
        for (int oversample = 1; oversample <= MAX_OVERSAMPLE; oversample++) {
            SCALED_FONT_IDS[oversample - 1] = ArcaneClient.id("ui_x" + oversample);
        }
    }

    private static Boolean caxtonUsable;
    private static Identifier styledId;
    private static Style cachedStyle;

    private ArcaneFont() {
    }

    /** Minecraft's own renderer, which is the only one that resolves fonts from a style. */
    public static TextRenderer renderer(MinecraftClient client) {
        return client.textRenderer;
    }

    /** A literal in the Arcane interface font. */
    public static Text text(String value) {
        return Text.literal(value).setStyle(style());
    }

    public static int width(TextRenderer renderer, String value) {
        return renderer.getWidth(text(value));
    }

    /** {@code value} cut down to {@code maxWidth}, measured in the interface font. */
    public static OrderedText trimmed(TextRenderer renderer, String value, int maxWidth) {
        return Language.getInstance().reorder(renderer.trimToWidth(text(value), maxWidth));
    }

    public static Style style() {
        Identifier id = fontId();
        if (!id.equals(styledId) || cachedStyle == null) {
            styledId = id;
            cachedStyle = Style.EMPTY.withFont(new StyleSpriteSource.Font(id));
        }
        return cachedStyle;
    }

    /** Re-checks whether the Caxton font is usable. Call after a resource reload. */
    public static void invalidate() {
        caxtonUsable = null;
        cachedStyle = null;
        styledId = null;
    }

    private static Identifier fontId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (caxtonUsable == null) {
            caxtonUsable = FabricLoader.getInstance().isModLoaded(CAXTON_MOD_ID) && storageExists(client, CAXTON_FONT_ID);
        }
        if (caxtonUsable) {
            return CAXTON_FONT_ID;
        }
        return SCALED_FONT_IDS[Math.clamp(client.getWindow().getScaleFactor(), 1, MAX_OVERSAMPLE) - 1];
    }

    /**
     * Caxton refuses to build its font providers when its native library is missing, which would
     * leave the definition unresolved and Arcane's text blank. Check before committing to it.
     */
    private static boolean storageExists(MinecraftClient client, Identifier id) {
        try {
            FontManager manager = ((MinecraftClientAccessor) client).arcaneclient$getFontManager();
            FontManagerAccessor fonts = (FontManagerAccessor) manager;
            return fonts.arcaneclient$getStorage(id) != fonts.arcaneclient$getStorage(FontManager.MISSING_STORAGE_ID);
        } catch (RuntimeException | LinkageError exception) {
            ArcaneClient.LOGGER.warn("Could not check the {} font; falling back to the bundled variants", id, exception);
            return false;
        }
    }
}
