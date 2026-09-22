package dev.arcaneclient.additions.cosmetics;

/** Fixed-size trail samples. Teleports break the ribbon, and stationary samples expire naturally. */
public final class TrailHistory {
    public static final int CAPACITY = 64;
    private final double[] x = new double[CAPACITY];
    private final double[] y = new double[CAPACITY];
    private final double[] z = new double[CAPACITY];
    private final long[] born = new long[CAPACITY];
    private int start;
    private int size;
    private long tick;

    public void clear() {
        start = 0;
        size = 0;
        tick = 0;
    }

    public void sample(double px, double py, double pz, int lifetime) {
        tick++;
        int limit = Math.clamp(lifetime, 6, 60);
        while (size > 0 && tick - born[start] >= limit) {
            start = (start + 1) % CAPACITY;
            size--;
        }
        if (size > 0) {
            int last = index(size - 1);
            double dx = px - x[last], dy = py - y[last], dz = pz - z[last];
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > 64) size = 0;
            else if (distance < 0.0025) return;
        }
        if (size == CAPACITY) {
            start = (start + 1) % CAPACITY;
            size--;
        }
        int slot = index(size++);
        x[slot] = px;
        y[slot] = py;
        z[slot] = pz;
        born[slot] = tick;
    }

    public int size() { return size; }
    public double x(int point) { return x[index(point)]; }
    public double y(int point) { return y[index(point)]; }
    public double z(int point) { return z[index(point)]; }
    public float opacity(int point, int lifetime, float partialTick) {
        return (float)Math.clamp(1.0 - (tick - born[index(point)] + partialTick) / Math.max(1.0, lifetime), 0.0, 1.0);
    }

    private int index(int point) { return (start + point) % CAPACITY; }
}
