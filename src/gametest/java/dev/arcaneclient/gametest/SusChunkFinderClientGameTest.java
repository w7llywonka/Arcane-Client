package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.additions.susfinder.SusChunkFinderConfig;
import dev.arcaneclient.additions.susfinder.SusChunkFinderController;
import dev.arcaneclient.additions.susfinder.SusChunkFinderRenderer;
import dev.arcaneclient.additions.susfinder.SusScoring;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

/** Real server packets exercise the independent score, all heights, terrain rendering and invalidation. */
@SuppressWarnings("UnstableApiUsage")
public final class SusChunkFinderClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        SusChunkFinderConfig previous = context.computeOnClient(client -> ArcaneClient.config().susFinder);
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null
                && client.levelRenderer.hasRenderedAllSections(), 5000);
            world.getServer().runCommand("gamerule minecraft:random_tick_speed 0");
            world.getServer().runCommand("gamemode spectator @a");
            ChunkPos chunk = context.computeOnClient(client -> {
                client.gui.setScreen(null);
                var all = ArcaneClient.config();
                all.enabled = false;
                all.cleanCapture = all.streamerMode = false;
                all.susFinder = new SusChunkFinderConfig();
                all.susFinder.kelp = all.susFinder.bamboo = all.susFinder.berries = all.susFinder.vines = all.susFinder.dripstone = false;
                all.susFinder.threshold = 14;
                all.susFinder.scanRange = 64;
                all.susFinder.scanBudget = 8192;
                return client.player.chunkPosition();
            });
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ();
            world.getServer().runCommand("tp @a " + (x + 8) + " 326 " + (z + 8));
            // A known column removes naturally generated growth from this negative-control chunk.
            for (int y = -64; y < 320; y += 64) {
                world.getServer().runCommand("fill " + x + " " + y + " " + z + " " + (x + 15) + " " + (y + 63) + " " + (z + 15) + " stone");
            }
            world.getServer().runCommand("setblock " + (x + 2) + " 100 " + (z + 2) + " chest");
            context.runOnClient(client -> ArcaneClient.config().susFinder.enabled = true);
            context.waitFor(client -> finder().snapshotAt(chunk.x(), chunk.z()) != null, 200);
            context.waitFor(client -> finder().maxChunksCompletedInTick() > 1, 200);
            context.runOnClient(client -> {
                require(finder().snapshotAt(chunk.x(), chunk.z()).counts().isEmpty(), "chest/stone-only chunk contributes no family");
                require(!flagged(chunk), "chest-only negative control remains unflagged");
                require(finder().maxChunksCompletedInTick() <= 12, "empty palette skipping batches several chunks with a hard completion cap");
                require(finder().lastTickInspectedBlocks() <= 8192 && finder().lastTickPaletteChecks() <= 192,
                    "all jobs share the same per-tick block and palette budgets");
            });
            for (int y : new int[]{-50, 300}) {
                world.getServer().runCommand("setblock " + (x + 4) + " " + y + " " + (z + 4) + " amethyst_cluster[facing=up]");
                world.getServer().runCommand("setblock " + (x + 7) + " " + y + " " + (z + 4) + " amethyst_cluster[facing=up]");
            }
            context.waitFor(client -> count(chunk) == 4 && flagged(chunk), 200);
            context.waitFor(client -> SusChunkFinderRenderer.renderedTileCount() > 0 && SusChunkFinderRenderer.renderedMarkerCount() > 0, 200);
            context.runOnClient(client -> {
                require(Math.abs(SusChunkFinderRenderer.renderedSurfaceY(chunk.x(), chunk.z(), 8, 8) - 320.04) < 0.01,
                    "highlight follows terrain at Y 320, not observer altitude");
                require(finder().zones().stream().anyMatch(zone -> zone.members().stream().anyMatch(c -> c.chunkX() == chunk.x() && c.chunkZ() == chunk.z())),
                    "candidate has one grouped marker");
                require(!ArcaneClient.config().enabled, "original growth finder remains independent and disabled");
            });
            world.getServer().runCommand("tp @a " + (x + 8) + " 344 " + (z + 8) + " 0 90");
            context.waitFor(client -> client.player.getY() > 343, 200);
            context.runOnClient(client -> {
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            });
            context.waitTicks(5);
            context.takeScreenshot("dev21-sus-ground-highlight-and-zone");
            world.getServer().runCommand("setblock " + (x + 4) + " -50 " + (z + 4) + " stone");
            context.waitFor(client -> count(chunk) == 3 && !flagged(chunk), 200);
            // Opt in after joining; capture a subsequent real block-light update, independent of Fullbright.
            context.runOnClient(client -> {
                ArcaneClient.config().susFinder.inferredAmethystLight = true;
                ArcaneClient.config().fullbright = true;
            });
            world.getServer().runCommand("fill " + (x + 5) + " 97 " + (z + 5) + " " + (x + 11) + " 103 " + (z + 11) + " air");
            world.getServer().runCommand("setblock " + (x + 8) + " 100 " + (z + 8) + " glowstone");
            try {
                // Vanilla only sends standalone light updates to watch-distance edge chunks.
                // This is an interior chunk, so unload/reload it to receive a genuine initial
                // ChunkData light payload after the source exists. No client lighting is copied.
                context.waitFor(client -> client.level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 10, 100, z + 8)) == 13, 200);
                world.getServer().runCommand("tp @a " + (x + 1032) + " 326 " + (z + 8));
                context.waitFor(client -> client.level.getChunkSource().getChunk(chunk.x(), chunk.z(), false) == null, 200);
                world.getServer().runCommand("tp @a " + (x + 8) + " 326 " + (z + 8));
                context.waitFor(client -> finder().receivedBlockLightAt(x + 10, 100, z + 8) == 13, 200);
            } catch (AssertionError failure) {
                String serverLight = world.getServer().computeOnServer(server -> {
                    var level = server.overworld();
                    return "source=" + level.getBlockState(new BlockPos(x + 8, 100, z + 8))
                        + ", air=" + level.getBlockState(new BlockPos(x + 10, 100, z + 8))
                        + ", blockLight=" + level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 8, 100, z + 8))
                        + "/" + level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 10, 100, z + 8))
                        + "/" + level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 11, 100, z + 8));
                });
                String clientLight = context.computeOnClient(client ->
                    finder().lightDiagnostics() + ", player=" + client.player.position()
                    + ", received=" + finder().receivedBlockLightAt(x + 8, 100, z + 8)
                    + "/" + finder().receivedBlockLightAt(x + 10, 100, z + 8)
                    + "/" + finder().receivedBlockLightAt(x + 11, 100, z + 8)
                    + ", vanilla=" + client.level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 8, 100, z + 8))
                    + "/" + client.level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 10, 100, z + 8))
                    + "/" + client.level.getBrightness(LightLayer.BLOCK, new BlockPos(x + 11, 100, z + 8)));
                throw new AssertionError("Sus raw-light packet verification failed. Server: " + serverLight + "; Client: " + clientLight, failure);
            }
            context.runOnClient(client -> {
                require(finder().cachedLightChunks() > 0 && finder().cachedLightChunks() <= 192, "server block-light cache populated within its bound");
                require(finder().receivedBlockLightAt(x + 11, 100, z + 8) == 12, "Fullbright did not flatten raw packet light");
                ArcaneClient.config().fullbright = false;
                ArcaneClient.LOGGER.info("[QA] Sus finder passed: chest-negative, weighted all-height growth, terrain tiles, grouped marker, removal refresh and raw light independent of Fullbright");
            });
        } finally {
            context.runOnClient(client -> {
                require(finder().scannedChunks() == 0 && finder().cachedLightChunks() == 0 && finder().zones().isEmpty(),
                    "disconnect clears results, zones and received light");
                ArcaneClient.config().susFinder = previous;
                ArcaneClient.config().fullbright = false;
            });
        }
    }
    private static SusChunkFinderController finder() { return SusChunkFinderController.instance(); }
    private static int count(ChunkPos chunk) {
        var snapshot = finder().snapshotAt(chunk.x(), chunk.z());
        return snapshot == null ? -1 : snapshot.counts().getOrDefault(SusScoring.Family.AMETHYST, 0);
    }
    private static boolean flagged(ChunkPos chunk) {
        return finder().candidates().stream().anyMatch(candidate -> candidate.chunkX() == chunk.x() && candidate.chunkZ() == chunk.z());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Sus Chunk Finder: " + message);
    }
}
