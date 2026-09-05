package dev.arcaneclient.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChunkTileRenderPolicyTest {
    @Test
    void tileUsesTerrainTopWithOnlyASmallZFightOffset() {
        assertEquals(-59.96, ChunkTileRenderPolicy.surfaceY(-60), 0.001);
        assertEquals(64.04, ChunkTileRenderPolicy.surfaceY(64), 0.001);
        assertEquals(199.04, ChunkTileRenderPolicy.surfaceY(199), 0.001);
    }

    @Test
    void tilePlacementAndColorsRemainValidAtWorldLimits() {
        assertEquals(320.04, ChunkTileRenderPolicy.surfaceY(320), 0.001);
        assertTrue(ChunkTileRenderPolicy.outlineColor(80) >>> 24 > ChunkTileRenderPolicy.fillColor(80) >>> 24);
        assertEquals(0, ChunkTileRenderPolicy.outlineColor(0, false));
        assertEquals(0, ChunkTileRenderPolicy.outlineColor(100, false));
        assertEquals(ChunkTileRenderPolicy.fillColor(80), ChunkTileRenderPolicy.fillColor(80, true));
    }
}
