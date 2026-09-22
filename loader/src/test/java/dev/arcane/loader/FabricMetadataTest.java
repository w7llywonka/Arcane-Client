package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class FabricMetadataTest {
    @Test
    void declaresBundledClientAsNestedRequiredFabricMod() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(input, "processed Fabric metadata should be on the test classpath");
            Map<String, Object> metadata = Json.parseObject(
                new String(input.readAllBytes(), StandardCharsets.UTF_8));

            assertEquals("arcaneloader", metadata.get("id"));
            assertEquals("ARCLoader", metadata.get("name"));
            assertEquals(">=2.9.4", objectMap(metadata.get("depends")).get("arcaneclient"));

            Object jarsValue = metadata.get("jars");
            assertTrue(jarsValue instanceof List<?>, "jars must be a list");
            List<?> jars = (List<?>) jarsValue;
            assertEquals(1, jars.size());
            assertEquals(
                "META-INF/jars/arcane-client-2.9.4+mc26.3.jar",
                objectMap(jars.getFirst()).get("file")
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        assertTrue(value instanceof Map<?, ?>, "expected an object");
        return (Map<String, Object>) value;
    }
}
