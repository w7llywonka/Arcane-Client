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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Scanner-only integration benchmark with deliberately non-empty candidate sections. */
@SuppressWarnings("UnstableApiUsage")
public final class ChunkScannerClientGameTest implements FabricClientGameTest {
    private static final int SLICE_BLOCKS = 2_048;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(640, 360);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null
                && client.levelRenderer.hasRenderedAllSections(), 5000);
            context.waitTicks(10);
            ScannerBenchmark benchmark = context.computeOnClient(client -> {
                require(client.level != null && client.player != null, "scanner benchmark needs a loaded world");
                ChunkPos center = client.player.chunkPosition();
                int observerSectionY = SectionPos.blockToSectionCoord(client.player.getBlockY());
                int sampleY = Math.clamp(client.player.getBlockY(), client.level.getMinY(), client.level.getMaxY());
                List<Sample> samples = new ArrayList<>();
                for (int dz = -2; dz <= 2; ++dz) {
                    for (int dx = -2; dx <= 2; ++dx) {
                        LevelChunk chunk = client.level.getChunkSource().getChunk(center.x() + dx, center.z() + dz, false);
                        if (chunk == null) continue;
                        BlockPos position = new BlockPos(chunk.getPos().getMinBlockX() + 8, sampleY, chunk.getPos().getMinBlockZ() + 8);
                        samples.add(new Sample(chunk, position, client.level.getBlockState(position)));
                    }
                }
                require(samples.size() >= 9, "not enough loaded chunks for scanner benchmark");
                Sample featureChunk = samples.getFirst();
                int trailTopY = Math.clamp(
                    sampleY,
                    client.level.getMinY() + 6,
                    client.level.getMaxY()
                );
                ArrayList<SampleBlock> featureBlocks = new ArrayList<>();
                for (int step = 0; step < 6; ++step) {
                    BlockPos position = new BlockPos(
                        featureChunk.chunk.getPos().getMinBlockX() + 2 + step,
                        trailTopY - step,
                        featureChunk.chunk.getPos().getMinBlockZ() + 2
                    );
                    featureBlocks.add(new SampleBlock(position, client.level.getBlockState(position)));
                }
                BlockPos shardPosition = new BlockPos(
                    featureChunk.chunk.getPos().getMinBlockX() + 10,
                    trailTopY,
                    featureChunk.chunk.getPos().getMinBlockZ() + 10
                );
                featureBlocks.add(new SampleBlock(shardPosition, client.level.getBlockState(shardPosition)));

                try {
                    for (Sample sample : samples) {
                        client.level.setBlock(sample.position, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    for (int step = 0; step < 6; ++step) {
                        client.level.setBlock(
                            featureBlocks.get(step).position,
                            Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE
                        );
                    }
                    client.level.setBlock(
                        shardPosition,
                        Blocks.SMALL_AMETHYST_BUD.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE
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
                        ChunkScanner.ChunkScanJob job = scanner.begin(client.level, sample.chunk, 0L, observerSectionY, false);
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
                    for (Sample sample : samples) client.level.setBlock(sample.position,
                        Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7, 7), Block.UPDATE_KNOWN_SHAPE);
                    long countStarted = System.nanoTime();
                    int paletteTotal = 0, countedSections = 0;
                    for (Sample sample : samples) {
                        var countJob = scanner.begin(client.level, sample.chunk, 1, observerSectionY, false, false, false, false);
                        while (!countJob.isComplete()) countJob.step(SLICE_BLOCKS);
                        require(countJob.inspectedBlocks() == 0, "Count-only path must never walk block coordinates");
                        paletteTotal += countJob.grownCount();
                        countedSections += countJob.countedSections();
                    }
                    long countNanos = System.nanoTime() - countStarted;
                    long bruteStarted = System.nanoTime();
                    int bruteTotal = 0;
                    for (Sample sample : samples) for (var section : sample.chunk.getSections())
                        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                            if (dev.arcaneclient.scan.GrownBlocks.matches(section.getBlockState(x, y, z))) bruteTotal++;
                    long bruteNanos = System.nanoTime() - bruteStarted;
                    require(paletteTotal == samples.size() && paletteTotal == bruteTotal, "Palette counts must equal exact coordinate counts");
                    require(countedSections == samples.size(), "Only the plant-bearing section in each chunk should be counted");
                    ArcaneClient.LOGGER.info("[QA] Plant-only benchmark: {} chunks, {} grown blocks, {} matching sections, zero coordinate inspections; palette {} ms vs full-coordinate {} ms",
                        samples.size(), paletteTotal, countedSections, countNanos / 1_000_000.0, bruteNanos / 1_000_000.0);
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
                        client.level.setBlock(sample.position, sample.previous, Block.UPDATE_ALL);
                    }
                    for (SampleBlock sample : featureBlocks) {
                        client.level.setBlock(sample.position, sample.previous, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            });

            require(benchmark.completedJobs == benchmark.sampledChunks, "not every scanner job completed");
            require(benchmark.evidenceChunks == 0, "Redstone and immature buds must not become growth evidence");
            require(benchmark.visitedBlocks <= 8192,
                "Unrelated redstone sections must be skipped; only optional shard/trail ESP needs positions");
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

    private record Sample(LevelChunk chunk, BlockPos position, BlockState previous) {
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
