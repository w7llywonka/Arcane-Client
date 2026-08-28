package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class RoundedTextureTest {
    @Test
    void fillMaskContainsTransparentSolidAndAntialiasedPixels() throws IOException {
        BufferedImage image = readImage("rounded_fill.png");
        assertEquals(68, image.getWidth());
        assertEquals(68, image.getHeight());
        assertEquals(0, alpha(image.getRGB(0, 0)));
        assertEquals(255, alpha(image.getRGB(34, 34)));

        boolean hasPartialAlpha = false;
        for (int y = 0; y < image.getHeight() && !hasPartialAlpha; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = alpha(image.getRGB(x, y));
                if (alpha > 0 && alpha < 255) {
                    hasPartialAlpha = true;
                    break;
                }
            }
        }
        assertTrue(hasPartialAlpha, "rounded mask must contain real antialias coverage");
    }

    @Test
    void outlineMaskHasASmoothEdgeAndTransparentCenter() throws IOException {
        BufferedImage image = readImage("rounded_outline.png");
        assertEquals(0, alpha(image.getRGB(34, 34)));
        boolean hasPartialAlpha = false;
        boolean hasOpaqueEdge = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = alpha(image.getRGB(x, y));
                hasPartialAlpha |= alpha > 0 && alpha < 255;
                hasOpaqueEdge |= alpha == 255;
            }
        }
        assertTrue(hasPartialAlpha, "outline mask must contain antialias coverage");
        assertTrue(hasOpaqueEdge, "outline mask must retain a solid one-pixel core");
    }

    @Test
    void standaloneTexturesRequestLinearFilteringAndClamping() throws IOException {
        for (String name : new String[]{"rounded_fill.png.mcmeta", "rounded_outline.png.mcmeta"}) {
            String path = "assets/arcaneclient/textures/gui/" + name;
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                String metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(metadata.contains("\"blur\": true"), path);
                assertTrue(metadata.contains("\"clamp\": true"), path);
            }
        }
    }

    private static BufferedImage readImage(String name) throws IOException {
        String path = "assets/arcaneclient/textures/gui/" + name;
        try (InputStream stream = RoundedTextureTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path);
            return image;
        }
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }
}