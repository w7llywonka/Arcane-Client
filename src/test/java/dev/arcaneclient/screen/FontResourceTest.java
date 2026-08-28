package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontLoader;
import net.minecraft.client.font.TrueTypeFontLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

final class FontResourceTest {
    @Test
    void everyScalePointsAtThePackagedSoraFontAndHasVanillaFallback() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("assets/arcaneclient/font/sora.ttf"));
        for (int scale = 1; scale <= 6; scale++) {
            String path = "assets/arcaneclient/font/ui_x" + scale + ".json";
            try (InputStream stream = loader.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(json.contains("\"file\": \"arcaneclient:sora.ttf\""), path);
                assertTrue(json.contains("\"id\": \"minecraft:default\""), path);
                assertTrue(json.contains("\"oversample\": " + scale + ".0"), path);
                JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("providers").forEach(provider ->
                    assertTrue(FontLoader.Provider.CODEC.parse(JsonOps.INSTANCE, provider).result().isPresent(), path)
                );
            }
        }
    }

    @Test
    void minecraftTrueTypeLoaderOpensSoraAndProvidesLatinGlyphs() throws Exception {
        Identifier definitionLocation = Identifier.of("arcaneclient", "sora.ttf");
        TrueTypeFontLoader definition = new TrueTypeFontLoader(
            definitionLocation,
            10.0f,
            3.0f,
            TrueTypeFontLoader.Shift.NONE,
            ""
        );
        FontLoader.Loadable loadable = definition.build().left().orElseThrow();
        AtomicReference<Identifier> opened = new AtomicReference<>();
        ResourceManager resources = classpathResources(opened);

        try (Font font = loadable.load(resources)) {
            assertTrue(font.getProvidedGlyphs().contains((int)'A'));
            assertTrue(font.getProvidedGlyphs().contains((int)'a'));
            assertTrue(font.getProvidedGlyphs().contains((int)'0'));
        }
        assertTrue(Identifier.of("arcaneclient", "font/sora.ttf").equals(opened.get()));
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
            public Set<String> getAllNamespaces() {
                return Set.of("arcaneclient");
            }

            @Override
            public List<Resource> getAllResources(Identifier id) {
                return List.of();
            }

            @Override
            public Map<Identifier, Resource> findResources(String startingPath, Predicate<Identifier> allowedPathPredicate) {
                return Map.of();
            }

            @Override
            public Map<Identifier, List<Resource>> findAllResources(String startingPath, Predicate<Identifier> allowedPathPredicate) {
                return Map.of();
            }

            @Override
            public Stream<ResourcePack> streamResourcePacks() {
                return Stream.empty();
            }
        };
    }
}