package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JsonTest {
    @Test
    void roundTripsProtocolObjects() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("active", true);
        original.put("tier", "base");
        original.put("timestamp", 1234L);
        assertEquals(original, Json.parseObject(Json.stringify(original)));
    }
}
