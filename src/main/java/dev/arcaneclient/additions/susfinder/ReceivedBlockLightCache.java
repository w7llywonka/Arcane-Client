package dev.arcaneclient.additions.susfinder;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/** Raw server BLOCK light only. Missing sections remain unknown; skylight and client brightness are never used. */
public final class ReceivedBlockLightCache {
    private static final byte[] ZERO = new byte[2048];
    private final int capacity;
    private final LinkedHashMap<Long, Map<Integer, byte[]>> chunks = new LinkedHashMap<>();
    private Object worldIdentity;

    public ReceivedBlockLightCache(int capacity) { this.capacity = Math.max(1, capacity); }

    public void bindWorld(Object identity) {
        if (worldIdentity != identity) { clear(); worldIdentity = identity; }
    }

    public void clear() { chunks.clear(); worldIdentity = null; }
    public void unload(long chunkKey) { chunks.remove(chunkKey); }
    public int size() { return chunks.size(); }
    public void retainLoaded(LongPredicate loaded) { chunks.keySet().removeIf(key -> !loaded.test(key)); }

    /** Mask bit zero is lightingProvider.getBottomY(), one section below the dimension's bottom section. */
    public boolean apply(long chunkKey, int bottomSection, int sectionCount, BitSet initialized,
                         BitSet empty, List<byte[]> nibbles) {
        if (sectionCount < 1 || sectionCount > 128 || initialized.length() > sectionCount
            || empty.length() > sectionCount || initialized.cardinality() != nibbles.size()) return false;
        BitSet overlap = (BitSet)initialized.clone();
        overlap.and(empty);
        if (!overlap.isEmpty()) return false;
        for (byte[] data : nibbles) if (data == null || data.length != 2048) return false;
        if (initialized.isEmpty() && empty.isEmpty()) return true;
        Map<Integer, byte[]> sections = chunks.remove(chunkKey);
        if (sections == null) sections = new HashMap<>();
        Iterator<byte[]> values = nibbles.iterator();
        for (int bit = initialized.nextSetBit(0); bit >= 0; bit = initialized.nextSetBit(bit + 1)) {
            sections.put(bottomSection + bit, values.next().clone());
        }
        for (int bit = empty.nextSetBit(0); bit >= 0; bit = empty.nextSetBit(bit + 1)) {
            sections.put(bottomSection + bit, ZERO);
        }
        chunks.put(chunkKey, sections);
        while (chunks.size() > capacity) chunks.remove(chunks.keySet().iterator().next());
        return true;
    }

    public int sample(int blockX, int blockY, int blockZ) {
        long key = chunkKey(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        Map<Integer, byte[]> sections = chunks.get(key);
        if (sections == null) return -1;
        byte[] data = sections.get(Math.floorDiv(blockY, 16));
        if (data == null) return -1;
        int index = (Math.floorMod(blockY, 16) << 8) | (Math.floorMod(blockZ, 16) << 4) | Math.floorMod(blockX, 16);
        return (data[index >>> 1] >>> ((index & 1) * 4)) & 15;
    }

    public static long chunkKey(int x, int z) { return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32; }
}
