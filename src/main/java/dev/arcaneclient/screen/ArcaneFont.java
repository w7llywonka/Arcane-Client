package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.mixin.FontManagerAccessor;
import dev.arcaneclient.mixin.MinecraftClientAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.EffectGlyph;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.GlyphsProvider;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class ArcaneFont {
    private static final Identifier FONT_ID = ArcaneClient.id("ui");

    private static FontStorage cachedStorage;
    private static TextRenderer cachedRenderer;
    private static boolean fallbackLogged;

    private ArcaneFont() {
    }

    public static TextRenderer renderer(MinecraftClient client) {
        try {
            FontStorage storage = ((FontManagerAccessor) ((MinecraftClientAccessor) client).arcaneclient$getFontManager())
                .arcaneclient$getStorage(FONT_ID);
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
