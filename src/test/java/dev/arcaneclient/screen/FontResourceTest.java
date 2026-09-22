package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

final class FontResourceTest {
    @Test
    void bothFontsHaveScaleMatchedBundledProvidersAndVanillaFallback() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("assets/arcaneclient/font/sora.ttf"));
        for (String family : new String[]{"ui", "sora"}) for (int scale = 1; scale <= 6; scale++) {
            String path = "assets/arcaneclient/font/" + family + "_x" + scale + ".json";
            try (InputStream stream = loader.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                var providers = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("providers");
                var font = providers.get(0).getAsJsonObject();
                assertEquals("arcaneclient:" + (family.equals("ui") ? "xuong" : "sora") + ".ttf", font.get("file").getAsString(), path);
                assertNotNull(loader.getResource("assets/arcaneclient/font/" + (family.equals("ui") ? "xuong" : "sora") + ".ttf"));
                assertEquals(7.25f, font.get("size").getAsFloat(), path);
                assertEquals(scale * 2.0f, font.get("oversample").getAsFloat(), path);
                assertEquals("minecraft:default", providers.get(1).getAsJsonObject().get("id").getAsString(), path);
                providers.forEach(provider ->
                    assertTrue(GlyphProviderDefinition.Conditional.CODEC.parse(JsonOps.INSTANCE, provider).result().isPresent(), path)
                );
            }
        }
    }

    @Test
    void minecraftTrueTypeLoaderProvidesSoraGlyphsAtEverySupersampledScale() throws Exception {
        Identifier definitionLocation = Identifier.fromNamespaceAndPath("arcaneclient", "sora.ttf");
        AtomicReference<Identifier> opened = new AtomicReference<>();
        ResourceManager resources = classpathResources(opened);
        float referenceAdvance = 0.0f;
        for (int scale = 1; scale <= 6; scale++) {
            TrueTypeGlyphProviderDefinition definition = new TrueTypeGlyphProviderDefinition(
                definitionLocation, 9.5f, scale * 2.0f, TrueTypeGlyphProviderDefinition.Shift.NONE, ""
            );
            GlyphProviderDefinition.Loader loadable = definition.unpack().left().orElseThrow();
            try (GlyphProvider font = loadable.load(resources)) {
                for (int codepoint : new int[] {'A', 'a', '0', '?', 0x2026}) {
                    assertTrue(font.getSupportedGlyphs().contains(codepoint));
                    var glyph = font.getGlyph(codepoint);
                    assertNotNull(glyph);
                    assertTrue(glyph.info().getAdvance() > 0.0f);
                }
                float advance = font.getGlyph('A').info().getAdvance();
                if (scale == 1) referenceAdvance = advance;
                else assertEquals(referenceAdvance, advance, 0.6f, "Supersampling must not multiply logical text width");
            }
        }
        assertEquals(Identifier.fromNamespaceAndPath("arcaneclient", "font/sora.ttf"), opened.get());
    }

    private static ResourceManager classpathResources(AtomicReference<Identifier> opened) {
        return new ResourceManager() {
            @Override
            public InputStream open(Identifier id) throws IOException {
                opened.set(id);
                String path = "assets/" + id.getNamespace() + "/" + id.getPath();
                InputStream stream = FontResourceTest.class.getClassLoader().getResourceAsStream(path);
                if (stream == null) throw new IOException("Missing classpath resource " + path);
                return stream;
            }

            @Override
            public Optional<Resource> getResource(Identifier id) {
                return Optional.empty();
            }

            @Override
            public Set<String> getNamespaces() {
                return Set.of("arcaneclient");
            }

            @Override
            public List<Resource> getResourceStack(Identifier id) {
                return List.of();
            }

            @Override
            public Map<Identifier, Resource> listResources(String startingPath, ResourceManager.Selector allowedPathPredicate) {
                return Map.of();
            }

            @Override
            public Map<Identifier, List<Resource>> listResourceStacks(String startingPath, ResourceManager.Selector allowedPathPredicate) {
                return Map.of();
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.empty();
            }
        };
    }
}
