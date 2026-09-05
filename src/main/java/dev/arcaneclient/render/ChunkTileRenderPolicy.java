package dev.arcaneclient.render;

/** Pure placement and color rules for scored chunk tiles. */
final class ChunkTileRenderPolicy {
    private ChunkTileRenderPolicy() {
    }

    static double surfaceY(int terrainTopY) {
        return terrainTopY + 0.04;
    }

    static int fillColor(int score) {
        if (score >= 75) return 0x70FF4545;
        if (score >= 50) return 0x60FF9D2E;
        return 0x50FFD84A;
    }

    static int fillColor(int score, boolean flagged) {
        return flagged ? fillColor(score) : 0x167BD945;
    }

    static int outlineColor(int score) {
        return 0xE8000000 | fillColor(score) & 0x00FFFFFF;
    }

    static int outlineColor(int score, boolean flagged) {
        return flagged ? outlineColor(score) : 0;
    }
}
