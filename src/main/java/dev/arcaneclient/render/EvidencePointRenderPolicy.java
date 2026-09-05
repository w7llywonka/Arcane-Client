package dev.arcaneclient.render;

import dev.arcaneclient.model.ChunkIntel;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.EvidencePoint;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Pure selection and color policy for the bounded Evidence Points world layer. */
@Environment(EnvType.CLIENT)
final class EvidencePointRenderPolicy {
    static final int MAX_POINTS = 512;

    private EvidencePointRenderPolicy() {
    }

    static boolean anyLayerEnabled(boolean chunkFinder, boolean chunkTiles, boolean evidencePoints) {
        return chunkFinder && (chunkTiles || evidencePoints);
    }

    static List<EvidencePoint> select(Iterable<ChunkIntel> chunks) {
        ArrayList<EvidencePoint> selected = new ArrayList<EvidencePoint>(MAX_POINTS);
        for (ChunkIntel intel : chunks) {
            for (EvidencePoint point : intel.evidencePoints()) {
                selected.add(point);
                if (selected.size() == MAX_POINTS) return List.copyOf(selected);
            }
        }
        return List.copyOf(selected);
    }

    static int color(EvidenceFamily family) {
        return switch (family) {
            case GROWTH -> 0xCC4ADE80;
            case FARM_GEOMETRY -> 0xCC84CC16;
            case HARVEST -> 0xCCF59E0B;
            case AUTOMATION -> 0xCCEF4444;
            case MANAGED_HABITAT -> 0xCC60A5FA;
            case AMETHYST_ACTIVITY -> 0xCCD8B4FE;
            case ACCESS_TRAIL -> 0xCC64748B;
            case PLAYER_PLACEMENT -> 0xCCC084FC;
            case LIGHTING -> 0xCCFDE047;
            case EXCAVATION -> 0xCC94A3B8;
            case INFRASTRUCTURE -> 0xCCFB7185;
            case OTHER -> 0xCCD4D4D8;
        };
    }
}
