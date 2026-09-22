package dev.arcaneclient.additions.susfinder;

import static org.junit.jupiter.api.Assertions.*;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceivedBlockLightCacheTest {
    private static BitSet mask(int... values) {
        var result = new BitSet(); for (int value : values) result.set(value); return result;
    }

    @Test void readsBothNibblesAtNegativeCoordinatesAndCorrectMaskSectionOrigin() {
        var cache = new ReceivedBlockLightCache(2);
        byte[] data = new byte[2048];
        data[2047] = (byte)0xA5;
        long key = ReceivedBlockLightCache.chunkKey(-1, -1);
        assertTrue(cache.apply(key, -5, 26, mask(1), mask(), List.of(data)));
        assertEquals(5, cache.sample(-2, -49, -1));
        assertEquals(10, cache.sample(-1, -49, -1));
        assertEquals(-1, cache.sample(-1, -65, -1));
        data[2047] = 0;
        assertEquals(10, cache.sample(-1, -49, -1), "packet arrays are copied");
    }

    @Test void partialUpdatesPreserveUntouchedSectionsAndExplicitEmptyClearsStaleLight() {
        var cache = new ReceivedBlockLightCache(2);
        byte[] data = new byte[2048]; data[0] = 5;
        cache.apply(0, -5, 26, mask(1, 2), mask(), List.of(data, data));
        cache.apply(0, -5, 26, mask(), mask(1), List.of());
        assertEquals(0, cache.sample(0, -64, 0));
        assertEquals(5, cache.sample(0, -48, 0));
        assertEquals(-1, cache.sample(0, -32, 0));
    }

    @Test void worldUnloadAndCapacityInvalidateReceivedData() {
        var cache = new ReceivedBlockLightCache(2);
        Object firstWorld = new Object(); cache.bindWorld(firstWorld);
        byte[] data = new byte[2048]; data[0] = 5;
        cache.apply(0, 0, 1, mask(0), mask(), List.of(data));
        cache.bindWorld(firstWorld);
        assertEquals(5, cache.sample(0, 0, 0));
        cache.apply(1, 0, 1, mask(0), mask(), List.of(data));
        cache.apply(2, 0, 1, mask(0), mask(), List.of(data));
        assertEquals(2, cache.size()); assertEquals(-1, cache.sample(0, 0, 0));
        cache.unload(1); assertEquals(-1, cache.sample(16, 0, 0));
        cache.bindWorld(new Object()); assertEquals(0, cache.size());
        cache.apply(1, 0, 1, mask(0), mask(), List.of(data));
        cache.retainLoaded(key -> false); assertEquals(0, cache.size());
    }

    @Test void malformedPacketsAreRejectedWithoutPartialMutation() {
        var cache = new ReceivedBlockLightCache(2);
        assertFalse(cache.apply(0, 0, 1, mask(0), mask(), List.of(new byte[1])));
        assertFalse(cache.apply(0, 0, 1, mask(0), mask(0), List.of(new byte[2048])));
        assertFalse(cache.apply(0, 0, 1, mask(1), mask(), List.of(new byte[2048])));
        assertFalse(cache.apply(0, 0, 1, mask(0), mask(), List.of()));
        assertEquals(0, cache.size());
    }
}
