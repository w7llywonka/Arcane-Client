package dev.arcaneclient.additions.susfinder;

/** Independent, opt-in heuristics over the world and light data already received by the client. */
public final class SusChunkFinderConfig {
    public boolean enabled;
    public boolean amethyst = true;
    public boolean kelp = true;
    public boolean bamboo = true;
    public boolean berries = true;
    public boolean vines = true;
    public boolean dripstone = true;
    public boolean inferredAmethystLight;
    public boolean highlights = true;
    public boolean markers = true;
    public boolean radar;
    /** Minimum suspicion score, not a probability or proof of player activity. */
    public int threshold = 55;
    public int mergeRadius = 32;
    public int scanRange = 192;
    public int scanBudget = 4096;
    public int fillOpacity = 32;
    public int outlineOpacity = 180;
    public int color = 0xFFF0B85A;
    public int inferredColor = 0xFFAB8CF5;

    public void sanitize() {
        threshold = Math.clamp(threshold, 1, 100);
        mergeRadius = Math.clamp(mergeRadius, 0, 128);
        scanRange = Math.clamp(scanRange, 32, 512);
        scanBudget = Math.clamp(scanBudget, 512, 16384);
        fillOpacity = Math.clamp(fillOpacity, 0, 160);
        outlineOpacity = Math.clamp(outlineOpacity, 0, 255);
        color |= 0xFF000000;
        inferredColor |= 0xFF000000;
    }

    public boolean enabled(SusScoring.Family family) {
        return switch (family) {
            case AMETHYST -> amethyst;
            case KELP -> kelp;
            case BAMBOO -> bamboo;
            case BERRIES -> berries;
            case VINES -> vines;
            case DRIPSTONE -> dripstone;
        };
    }

    int scanSignature() {
        return java.util.Objects.hash(enabled, amethyst, kelp, bamboo, berries, vines, dripstone,
            inferredAmethystLight, scanRange);
    }
}
