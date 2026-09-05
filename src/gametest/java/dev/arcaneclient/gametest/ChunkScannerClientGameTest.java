package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.model.EvidenceFamily;
import dev.arcaneclient.model.WorldObservation;
import dev.arcaneclient.scan.ChunkScanner;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;

/** Scanner-only integration benchmark with deliberately non-empty candidate sections. */
@SuppressWarnings("UnstableApiUsage")
public final class ChunkScannerClientGameTest implements FabricClientGameTest {
    private static final int SLICE_BLOCKS = 2_048;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(640, 360);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            ScannerBenchmark benchmark = context.computeOnClient(client -> {
                require(client.world != null && client.player != null, "scanner benchmark needs a loaded world");
                ChunkPos center = client.player.getChunkPos();
                int observerSectionY = ChunkSectionPos.getSectionCoord(client.player.getBlockY());
                int sampleY = Math.clamp(client.player.getBlockY(), client.world.getBottomY(), client.world.getTopYInclusive());
                List<Sample> samples = new ArrayList<>();
                for (int dz = -2; dz <= 2; ++dz) {
                    for (int dx = -2; dx <= 2; ++dx) {
                        WorldChunk chunk = client.world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz, false);
                        if (chunk == null) continue;
                        BlockPos position = new BlockPos(chunk.getPos().getStartX() + 8, sampleY, chunk.getPos().getStartZ() + 8);
                        samples.add(new Sample(chunk, position, client.world.getBlockState(position)));
                    }
                }
                require(samples.size() >= 9, "not enough loaded chunks for scanner benchmark");
                Sample featureChunk = samples.getFirst();
                int trailTopY = Math.clamp(
                    sampleY,
                    client.world.getBottomY() + 6,
                    client.world.getTopYInclusive()
                );
                ArrayList<SampleBlock> featureBlocks = new ArrayList<>();
                for (int step = 0; step < 6; ++step) {
                    BlockPos position = new BlockPos(
                        featureChunk.chunk.getPos().getStartX() + 2 + step,
                        trailTopY - step,
                        featureChunk.chunk.getPos().getStartZ() + 2
                    );
                    featureBlocks.add(new SampleBlock(position, client.world.getBlockState(position)));
                }
                BlockPos shardPosition = new BlockPos(
                    featureChunk.chunk.getPos().getStartX() + 10,
                    trailTopY,
                    featureChunk.chunk.getPos().getStartZ() + 10
                );
                featureBlocks.add(new SampleBlock(shardPosition, client.world.getBlockState(shardPosition)));

                try {
                    for (Sample sample : samples) {
                        client.world.setBlockState(sample.position, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                    }
                    for (int step = 0; step < 6; ++step) {
                        client.world.setBlockState(
                            featureBlocks.get(step).position,
                            Blocks.COBBLED_DEEPSLATE.getDefaultState(),
                            Block.NOTIFY_ALL | Block.FORCE_STATE
                        );
                    }
                    client.world.setBlockState(
                        shardPosition,
                        Blocks.SMALL_AMETHYST_BUD.getDefaultState(),
                        Block.NOTIFY_ALL | Block.FORCE_STATE
                    );
                    ChunkScanner scanner = new ChunkScanner();
                    int completedJobs = 0;
                    int visitedBlocks = 0;
                    int slices = 0;
                    int zeroWorkSlices = 0;
                    int evidenceChunks = 0;
                    boolean shardSeen = false;
                    boolean trailSeen = false;
                    boolean displayOnlyEvidenceSeen = false;
                    long started = System.nanoTime();
                    for (Sample sample : samples) {
                        ChunkScanner.ChunkScanJob job = scanner.begin(client.world, sample.chunk, 0L, observerSectionY, false);
                        int jobSlices = 0;
                        while (!job.isComplete()) {
                            int visitedThisSlice = job.step(SLICE_BLOCKS);
                            visitedBlocks += visitedThisSlice;
                            if (visitedThisSlice == 0) {
                                ++zeroWorkSlices;
                            }
                            ++slices;
                            require(++jobSlices <= 256, "scanner job did not complete within 256 slices");
                        }
                        ++completedJobs;
                        if (job.result().reasons().stream().anyMatch(reason -> reason.startsWith("redstone machinery"))) {
                            ++evidenceChunks;
                        }
                        if (sample.chunk == featureChunk.chunk) {
                            shardSeen = job.worldObservations().stream().anyMatch(observation ->
                                observation.kind() == WorldObservation.Kind.AMETHYST_SHARD
                                    && observation.position().x() == shardPosition.getX()
                                    && observation.position().y() == shardPosition.getY()
                                    && observation.position().z() == shardPosition.getZ()
                                    && observation.stage() == 0
                            );
                            trailSeen = job.worldObservations().stream().filter(observation ->
                                observation.kind() == WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL
                            ).count() >= 6;
                            displayOnlyEvidenceSeen = job.result().evidence().stream().anyMatch(evidence ->
                                evidence.family() == EvidenceFamily.AMETHYST_ACTIVITY
                                    || evidence.family() == EvidenceFamily.ACCESS_TRAIL
                            );
                        }
                    }
                    long elapsedNanos = System.nanoTime() - started;
                    return new ScannerBenchmark(
                        samples.size(),
                        completedJobs,
                        evidenceChunks,
                        visitedBlocks,
                        slices,
                        zeroWorkSlices,
                        shardSeen,
                        trailSeen,
                        displayOnlyEvidenceSeen,
                        elapsedNanos
                    );
                } finally {
                    for (Sample sample : samples) {
                        client.world.setBlockState(sample.position, sample.previous, Block.NOTIFY_ALL);
                    }
                    for (SampleBlock sample : featureBlocks) {
                        client.world.setBlockState(sample.position, sample.previous, Block.NOTIFY_ALL | Block.FORCE_STATE);
                    }
                }
            });

            require(benchmark.completedJobs == benchmark.sampledChunks, "not every scanner job completed");
            require(benchmark.evidenceChunks == benchmark.sampledChunks, "candidate blocks did not produce evidence in every sampled chunk");
            require(benchmark.visitedBlocks >= benchmark.sampledChunks * 4_096,
                "benchmark did not exercise a full non-empty candidate section per chunk");
            require(benchmark.slices >= benchmark.sampledChunks * 2,
                "benchmark did not exercise the 2,048-block incremental scan slices");
            require(benchmark.zeroWorkSlices == 0,
                "candidate-section jobs needed zero-work completion slices");
            require(benchmark.shardSeen, "scanner did not export the exact loaded amethyst shard");
            require(benchmark.trailSeen, "scanner did not export the connected descending cobbled-deepslate trail");
            require(!benchmark.displayOnlyEvidenceSeen,
                "amethyst or access-trail observations leaked into Chunk Finder scoring");
            ArcaneClient.LOGGER.info(
                "[QA] Authoritative scanner benchmark: {}/{} jobs completed, {} candidate-section blocks in {} slices ({} zero-work), {} evidence chunks, {} ms",
                benchmark.completedJobs,
                benchmark.sampledChunks,
                benchmark.visitedBlocks,
                benchmark.slices,
                benchmark.zeroWorkSlices,
                benchmark.evidenceChunks,
                benchmark.elapsedNanos / 1_000_000.0
            );
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Sample(WorldChunk chunk, BlockPos position, BlockState previous) {
    }

    private record SampleBlock(BlockPos position, BlockState previous) {
    }

    private record ScannerBenchmark(
        int sampledChunks,
        int completedJobs,
        int evidenceChunks,
        int visitedBlocks,
        int slices,
        int zeroWorkSlices,
        boolean shardSeen,
        boolean trailSeen,
        boolean displayOnlyEvidenceSeen,
        long elapsedNanos
    ) {
    }
}
