package dev.arcaneclient.additions.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.render.ArcaneVisibility;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Owns at most two tinted copies; vanilla textures and item components are never modified. */
@Environment(EnvType.CLIENT)
public final class CustomGlint {
    private static final int MAX_PIXELS = 1024 * 1024;
    private static final Map<Identifier, CachedTexture> CACHE = new HashMap<>(2);

    private CustomGlint() { }

    /** Called on the render thread while RenderSetup resolves textures for a draw. */
    public static AbstractTexture texture(Identifier id, AbstractTexture original) {
        if (!id.equals(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM) && !id.equals(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR)) {
            return original;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || ArcaneClient.config() == null
            || !ArcaneClient.config().visualAdditions.customGlint || ArcaneVisibility.overlaysHidden()) {
            return original;
        }
        int color = ArcaneClient.config().visualAdditions.glintColor & 0xFFFFFF;
        GpuTextureView sourceView = original.getTextureView();
        CachedTexture cached = CACHE.get(id);
        // ReloadableTexture.load replaces its GPU view on every resource reload,
        // even when TextureManager retains the same AbstractTexture instance.
        if (cached != null && cached.sourceView == sourceView && cached.color == color) {
            return cached.texture == null ? original : cached.texture;
        }
        if (cached != null && cached.texture != null) cached.texture.close();
        DynamicTexture tinted = create(client, id, original, color);
        // Keep failed attempts too, so a bad pack image cannot cause per-frame I/O.
        CACHE.put(id, new CachedTexture(sourceView, color, tinted));
        return tinted == null ? original : tinted;
    }

    private static DynamicTexture create(Minecraft client, Identifier id, AbstractTexture original, int tint) {
        NativeImage image = null;
        try {
            try (InputStream stream = client.getResourceManager().open(id)) {
                image = NativeImage.read(stream);
            }
            if ((long)image.getWidth() * image.getHeight() > MAX_PIXELS) {
                ArcaneClient.LOGGER.warn("Custom Glint uses the original oversized texture {} ({}x{})", id, image.getWidth(), image.getHeight());
                return null;
            }
            int red = (tint >>> 16) & 255;
            int green = (tint >>> 8) & 255;
            int blue = tint & 255;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    // Preserve alpha and the glint pattern's brightness, replacing
                    // its original purple hue with the chosen color.
                    int brightness = Math.max((pixel >>> 16) & 255, Math.max((pixel >>> 8) & 255, pixel & 255));
                    int r = (red * brightness + 127) / 255;
                    int g = (green * brightness + 127) / 255;
                    int b = (blue * brightness + 127) / 255;
                    image.setPixel(x, y, (pixel & 0xFF000000) | (r << 16) | (g << 8) | b);
                }
            }
            DynamicTexture result = new TintedTexture(id, image, original);
            image = null; // Ownership transfers to the texture, released by reset/rebuild.
            return result;
        } catch (IOException | RuntimeException failure) {
            ArcaneClient.LOGGER.warn("Could not tint glint texture {}; using the original", id, failure);
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    /** Tick/reset/shutdown calls occur on the client render thread. */
    public static void reset() {
        for (CachedTexture cached : CACHE.values()) {
            if (cached.texture != null) cached.texture.close();
        }
        CACHE.clear();
    }

    private record CachedTexture(GpuTextureView sourceView, int color, DynamicTexture texture) { }

    private static final class TintedTexture extends DynamicTexture {
        private TintedTexture(Identifier id, NativeImage image, AbstractTexture original) {
            super(() -> "Arcane tinted " + id, image);
            sampler = original.getSampler();
        }
    }
}
