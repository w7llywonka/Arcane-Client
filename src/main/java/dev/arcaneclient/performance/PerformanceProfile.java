package dev.arcaneclient.performance;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum PerformanceProfile {
    HIGH_FPS("HIGH FPS", 400_000L, 200_000L, 20, 6, 256, 10, 128, 384, 384, false),
    BALANCED("BALANCED", 800_000L, 350_000L, 10, 8, 512, 5, 256, 768, 768, true),
    QUALITY("QUALITY", 1_000_000L, 500_000L, 5, 10, 1_024, 3, 512, 1_500, 1_500, true);

    private static final PerformanceProfile[] VALUES = values();

    private final String label;
    private final long baseScanBudgetNanos;
    private final long scanBudgetPerIntensityNanos;
    private final int snapshotRefreshTicks;
    private final int storageRadiusChunks;
    private final int storageTargetLimit;
    private final int itemRefreshTicks;
    private final int itemTargetLimit;
    private final int tunnelTargetLimit;
    private final int markerTargetLimit;
    private final boolean filledStorageBoxes;

    PerformanceProfile(
        String label,
        long baseScanBudgetNanos,
        long scanBudgetPerIntensityNanos,
        int snapshotRefreshTicks,
        int storageRadiusChunks,
        int storageTargetLimit,
        int itemRefreshTicks,
        int itemTargetLimit,
        int tunnelTargetLimit,
        int markerTargetLimit,
        boolean filledStorageBoxes
    ) {
        this.label = label;
        this.baseScanBudgetNanos = baseScanBudgetNanos;
        this.scanBudgetPerIntensityNanos = scanBudgetPerIntensityNanos;
        this.snapshotRefreshTicks = snapshotRefreshTicks;
        this.storageRadiusChunks = storageRadiusChunks;
        this.storageTargetLimit = storageTargetLimit;
        this.itemRefreshTicks = itemRefreshTicks;
        this.itemTargetLimit = itemTargetLimit;
        this.tunnelTargetLimit = tunnelTargetLimit;
        this.markerTargetLimit = markerTargetLimit;
        this.filledStorageBoxes = filledStorageBoxes;
    }

    public String label() {
        return this.label;
    }

    public long scanBudgetNanos(int intensity) {
        return this.baseScanBudgetNanos + (long) Math.clamp(intensity, 1, 8) * this.scanBudgetPerIntensityNanos;
    }

    public int snapshotRefreshTicks() {
        return this.snapshotRefreshTicks;
    }

    public int storageRadiusChunks() {
        return this.storageRadiusChunks;
    }

    public int storageTargetLimit() {
        return this.storageTargetLimit;
    }

    public int itemRefreshTicks() {
        return this.itemRefreshTicks;
    }

    public int itemTargetLimit() {
        return this.itemTargetLimit;
    }

    public int tunnelTargetLimit() {
        return this.tunnelTargetLimit;
    }

    public int markerTargetLimit() {
        return this.markerTargetLimit;
    }

    public boolean filledStorageBoxes() {
        return this.filledStorageBoxes;
    }

    public PerformanceProfile next() {
        return VALUES[(this.ordinal() + 1) % VALUES.length];
    }

    public static PerformanceProfile fromConfig(int value) {
        return VALUES[Math.clamp(value, 0, VALUES.length - 1)];
    }
}
