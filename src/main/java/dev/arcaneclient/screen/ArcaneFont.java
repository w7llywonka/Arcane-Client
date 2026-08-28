package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.mixin.FontManagerAccessor;
import dev.arcaneclient.mixin.MinecraftClientAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.EffectGlyph;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.GlyphsProvider;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

/**
 * Supplies the smooth Arcane text renderer at the sharpness the player's GUI scale actually needs.
 *
 * <p>Minecraft bakes a TrueType glyph once, at {@code size * oversample} pixels, and samples the
 * glyph atlas with {@code FilterMode.NEAREST} — there is no filtering anywhere in that path. A glyph
 * is then drawn at {@code size * guiScale} physical pixels. So unless {@code oversample} equals the
 * GUI scale, every glyph is an antialiased bitmap resampled with no interpolation, which is what
 * makes the text look blocky. A single {@code oversample} baked into the font JSON can only ever be
 * right for one GUI scale.
 *
 * <p>Glyph metrics are all divided by {@code oversample}, so the variants below lay out identically
 * to the pixel and differ only in texture resolution. That lets us pick the one matching the current
 * GUI scale and get a 1:1 texel-to-pixel mapping — crisp text at every scale, with no changes to any
 * layout maths and no resource-pack or mod dependency.
 */
@Environment(EnvType.CLIENT)
public final class ArcaneFont {
    private static final int MAX_OVERSAMPLE = 6;
    private static final Identifier FALLBACK_FONT_ID = ArcaneClient.id("ui");
    private static final Identifier[] FONT_IDS = new Identifier[MAX_OVERSAMPLE];

    static {
        for (int oversample = 1; oversample <= MAX_OVERSAMPLE; oversample++) {
            FONT_IDS[oversample - 1] = ArcaneClient.id("ui_x" + oversample);
        }
    }

    private static FontStorage cachedStorage;
    private static TextRenderer cachedRenderer;
    private static boolean fallbackLogged;

    private ArcaneFont() {
    }

    public static TextRenderer renderer(MinecraftClient client) {
        try {
            FontManager manager = ((MinecraftClientAccessor) client).arcaneclient$getFontManager();
            FontManagerAccessor fonts = (FontManagerAccessor) manager;
            FontStorage storage = fonts.arcaneclient$getStorage(fontId(client));
            if (storage == fonts.arcaneclient$getStorage(FontManager.MISSING_STORAGE_ID)) {
                storage = fonts.arcaneclient$getStorage(FALLBACK_FONT_ID);
            }
            if (storage != cachedStorage || cachedRenderer == null) {
                cachedStorage = storage;
                cachedRenderer = new TextRenderer(new FixedGlyphsProvider(storage));
            }
            return cachedRenderer;
        } catch (RuntimeException | LinkageError exception) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                ArcaneClient.LOGGER.warn("Smooth Arcane font unavailable; using Minecraft's default renderer", exception);
            }
            return client.textRenderer;
        }
    }

    /** The font baked closest to one texel per physical pixel for the current GUI scale. */
    private static Identifier fontId(MinecraftClient client) {
        return FONT_IDS[Math.clamp(client.getWindow().getScaleFactor(), 1, MAX_OVERSAMPLE) - 1];
    }

    private record FixedGlyphsProvider(FontStorage storage) implements GlyphsProvider {
        @Override
        public GlyphProvider getGlyphs(StyleSpriteSource ignored) {
            return this.storage.getGlyphs(false);
        }

        @Override
        public EffectGlyph getRectangleGlyph() {
            return this.storage.getRectangleBakedGlyph();
        }
    }
}
