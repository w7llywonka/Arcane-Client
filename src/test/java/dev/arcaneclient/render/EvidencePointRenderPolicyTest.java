package dev.arcaneclient.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ChunkIntel;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.EvidencePoint;
import dev.arcaneclient.model.SignalCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EvidencePointRenderPolicyTest {
    @Test
    void evidencePointsRemainIndependentFromChunkTiles() {
        assertTrue(EvidencePointRenderPolicy.anyLayerEnabled(true, false, true));
        assertTrue(EvidencePointRenderPolicy.anyLayerEnabled(true, true, false));
        assertFalse(EvidencePointRenderPolicy.anyLayerEnabled(true, false, false));
        assertFalse(EvidencePointRenderPolicy.anyLayerEnabled(false, true, true));
    }

    @Test
    void globalSelectionStopsAtHardCapInMarkerPriorityOrder() {
        ArrayList<ChunkIntel> chunks = new ArrayList<ChunkIntel>();
        EvidenceFamily[] families = EvidenceFamily.values();
        for (int chunk = 0; chunk < 9; ++chunk) {
            ArrayList<EvidencePoint> points = new ArrayList<EvidencePoint>();
            for (int index = 0; index < 64; ++index) {
                points.add(point(chunk * 1000 + index, families[index % families.length]));
            }
            chunks.add(new ChunkIntel(80, 1, 1, 100, 100, families.length, points));
        }

        List<EvidencePoint> selected = EvidencePointRenderPolicy.select(chunks);

        assertEquals(EvidencePointRenderPolicy.MAX_POINTS, selected.size());
        assertEquals(0, selected.getFirst().position().x());
        assertEquals(7063, selected.getLast().position().x());
    }

    @Test
    void everyEvidenceFamilyHasItsOwnOpaqueEnoughColor() {
        long distinct = Arrays.stream(EvidenceFamily.values())
            .mapToInt(EvidencePointRenderPolicy::color)
            .distinct()
            .count();
        assertEquals(EvidenceFamily.values().length, distinct);
        for (EvidenceFamily family : EvidenceFamily.values()) {
            assertTrue((EvidencePointRenderPolicy.color(family) >>> 24) >= 0x80);
        }
    }

    private static EvidencePoint point(int x, EvidenceFamily family) {
        return new EvidencePoint(
            SignalCategory.INTERACTION,
            family,
            new BlockPosition(x, 64, 0),
            "test evidence " + x,
            50,
            10L,
            1,
            false
        );
    }
}
