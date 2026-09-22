package dev.arcaneclient.scan;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** On-demand, read-only check of received blocks; independent of scanner filters and queues. */
public final class AmethystDiagnostics {
    private AmethystDiagnostics() {}

    public record Counts(int chunks, int shell, int budding, int small, int medium, int large, int cluster) {
        public String stages() { return small + "/" + medium + "/" + large + "/" + cluster; }
        public int growthBlocks() { return small + medium + large + cluster; }
    }

    public static Counts read(ClientLevel world, ChunkPos center) {
        int[] counts = new int[7];
        // Fixed 5x5 area, all heights. Never requests chunks or sends server packets.
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                var chunk = world.getChunkSource().getChunk(center.x() + dx, center.z() + dz, false);
                if (chunk == null) continue;
                counts[0]++;
                for (var section : chunk.getSections()) {
                    if (section.hasOnlyAir()) continue;
                    section.getStates().count((state, count) -> {
                        int index = state.is(Blocks.AMETHYST_BLOCK) ? 1
                            : state.is(Blocks.BUDDING_AMETHYST) ? 2
                            : state.is(Blocks.SMALL_AMETHYST_BUD) ? 3
                            : state.is(Blocks.MEDIUM_AMETHYST_BUD) ? 4
                            : state.is(Blocks.LARGE_AMETHYST_BUD) ? 5
                            : state.is(Blocks.AMETHYST_CLUSTER) ? 6 : -1;
                        if (index >= 0) counts[index] += count;
                    });
                }
            }
        }
        return new Counts(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6]);
    }
}
