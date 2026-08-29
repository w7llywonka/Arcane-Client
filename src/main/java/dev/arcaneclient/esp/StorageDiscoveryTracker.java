package dev.arcaneclient.esp;

import java.util.Iterator;
import java.util.LinkedHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class StorageDiscoveryTracker {
    private final int capacity;
    private final LinkedHashSet<Long> seen = new LinkedHashSet<>();

    public StorageDiscoveryTracker(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public boolean markNew(long position) {
        if (!this.seen.add(position)) {
            return false;
        }
        while (this.seen.size() > this.capacity) {
            Iterator<Long> iterator = this.seen.iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public void reset() {
        this.seen.clear();
    }

    public int size() {
        return this.seen.size();
    }
}
