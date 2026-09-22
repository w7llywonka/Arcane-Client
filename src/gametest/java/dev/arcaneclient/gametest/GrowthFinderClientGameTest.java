package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.render.EspRenderer;
import dev.arcaneclient.scan.GrownBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;

/** Count thresholds, live invalidation and ESP run against real integrated-server packets. */
@SuppressWarnings("UnstableApiUsage")
public final class GrowthFinderClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("gamerule minecraft:random_tick_speed 0");
            ChunkPos chunk = context.computeOnClient(client -> {
                client.setScreen(null);
                var config = ArcaneClient.config();
                config.enabled = true;
                config.grownBlocksRequired = 3;
                config.scanRadius = 2;
                config.chunksPerTick = 16;
                config.evidencePoints = config.amethystEsp = config.accessTrailEsp = config.tunnelEsp = false;
                config.esp = false;
                config.blockEntityDebug = true;
                config.blockEntityDebugTracers = false;
                ArcaneClient.engine().clearCurrent();
                ArcaneClient.engine().queueNearby(client);
                int checked = 0;
                for (var block : BuiltInRegistries.BLOCK) {
                    var state = block.defaultBlockState();
                    if (!state.hasBlockEntity()) continue;
                    require(!GrownBlocks.matches(state), "Block entities must never count");
                    checked++;
                }
                require(checked > 20, "Registry exclusion must cover block-entity types");
                return client.player.chunkPosition();
            });
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ();
            for (int i = 0; i < 5; i++) {
                String type = new String[]{"chest", "ender_chest", "barrel", "furnace", "beacon"}[i];
                world.getServer().runCommand("setblock " + (x + i + 2) + " -10 " + (z + 2) + " " + type);
                world.getServer().runCommand("setblock " + (x + i + 2) + " 90 " + (z + 2) + " " + type);
            }
            world.getServer().runCommand("setblock " + (x + 3) + " 70 " + (z + 5) + " budding_amethyst");
            world.getServer().runCommand("setblock " + (x + 3) + " 71 " + (z + 5) + " small_amethyst_bud[facing=up]");
            context.waitFor(client -> EspRenderer.debugTargetCount() >= 5, 5000);
            context.runOnClient(client -> {
                ArcaneClient.engine().settingsChanged(client);
                require(ArcaneClient.engine().grownCountAt(chunk.x, chunk.z) == 0, "Containers and immature buds count zero");
                require(!flagged(chunk), "No grown blocks must stay unflagged");
                require(EspRenderer.renderedDebugTracerCount() == 0, "Tracers default off");
                ArcaneClient.config().blockEntityDebugTracers = true;
            });
            context.waitFor(client -> EspRenderer.renderedDebugTracerCount() >= 5, 5000);
            world.getServer().runCommand("setblock " + (x + 3) + " 71 " + (z + 5) + " amethyst_cluster[facing=up]");
            context.waitFor(client -> ArcaneClient.engine().grownCountAt(chunk.x, chunk.z) == 1, 5000);
            context.runOnClient(client -> {
                ArcaneClient.engine().settingsChanged(client);
                require(!flagged(chunk), "One block must not pass a three-block minimum");
                ArcaneClient.config().setSensitivity(100);
                ArcaneClient.engine().settingsChanged(client);
                require(ArcaneClient.config().grownBlocksRequired == 1 && flagged(chunk),
                    "100% sensitivity must flag one grown block immediately, without event/time gates");
                ArcaneClient.config().grownBlocksRequired = 5;
                ArcaneClient.engine().settingsChanged(client);
                require(!flagged(chunk), "Raising the slider must immediately hide lower-count chunks");
            });
            world.getServer().runCommand("setblock " + (x + 6) + " 70 " + (z + 8) + " grass_block");
            world.getServer().runCommand("setblock " + (x + 6) + " 71 " + (z + 8) + " sweet_berry_bush[age=3]");
            world.getServer().runCommand("setblock " + (x + 8) + " 67 " + (z + 8) + " dirt");
            world.getServer().runCommand("fill " + (x + 8) + " 68 " + (z + 8) + " " + (x + 8) + " 72 " + (z + 8) + " water");
            world.getServer().runCommand("setblock " + (x + 8) + " 68 " + (z + 8) + " kelp");
            world.getServer().runCommand("setblock " + (x + 8) + " 69 " + (z + 8) + " kelp");
            world.getServer().runCommand("setblock " + (x + 8) + " 68 " + (z + 8) + " kelp_plant");
            world.getServer().runCommand("setblock " + (x + 13) + " 71 " + (z + 8) + " stone");
            world.getServer().runCommand("setblock " + (x + 12) + " 71 " + (z + 8) + " vine[east=true]");
            world.getServer().runCommand("setblock " + (x + 10) + " 70 " + (z + 8) + " farmland[moisture=7]");
            world.getServer().runCommand("setblock " + (x + 10) + " 71 " + (z + 8) + " wheat[age=7]");
            context.waitFor(client -> ArcaneClient.engine().grownCountAt(chunk.x, chunk.z) == 5, 5000);
            context.runOnClient(client -> {
                ArcaneClient.engine().settingsChanged(client);
                require(flagged(chunk), "Cluster + berries + kelp stem + vine + mature wheat must meet five-block threshold");
                ArcaneClient.config().evidencePoints = true;
                ArcaneClient.engine().queueNearby(client);
            });
            context.waitFor(client -> {
                var marker = ArcaneClient.engine().snapshotMarkerAt(chunk.x, chunk.z);
                return marker != null && marker.intel().evidencePoints().size() == 5;
            }, 5000);
            context.runOnClient(client -> {
                var marker = ArcaneClient.engine().snapshotMarkerAt(chunk.x, chunk.z);
                require(marker.intel().evidencePoints().stream().allMatch(point ->
                    GrownBlocks.matches(client.world.getBlockState(new net.minecraft.util.math.BlockPos(
                        point.position().x(), point.position().y(), point.position().z())))),
                    "Evidence markers must sit on actual grown plants, not chunk centers");
            });
            world.getServer().runCommand("setblock " + (x + 3) + " 71 " + (z + 5) + " air");
            context.waitFor(client -> ArcaneClient.engine().grownCountAt(chunk.x, chunk.z) == 4, 5000);
            context.runOnClient(client -> {
                ArcaneClient.engine().settingsChanged(client);
                require(!flagged(chunk), "Removing a plant must remove the flag, not retain the peak count");
                ArcaneClient.config().blockEntityDebug = ArcaneClient.config().blockEntityDebugTracers = false;
            });
            // Place every stage on both sides of Y=0 while Amethyst ESP is off.
            String[] stages = {"small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster"};
            for (int y : new int[]{-10, 95}) {
                for (int i = 0; i < stages.length; i++) {
                    world.getServer().runCommand("setblock " + (x + 2 + i * 2) + " " + (y - 1) + " " + (z + 12) + " budding_amethyst");
                    world.getServer().runCommand("setblock " + (x + 2 + i * 2) + " " + y + " " + (z + 12) + " " + stages[i] + "[facing=up]");
                }
            }
            context.waitFor(client -> dev.arcaneclient.scan.AmethystDiagnostics.read(client.level, chunk).growthBlocks() == 8, 5000);
            context.runOnClient(client -> {
                var counts = dev.arcaneclient.scan.AmethystDiagnostics.read(client.level, chunk);
                require(counts.stages().equals("2/2/2/2"), "Raw received-block diagnostic must count each stage at both heights");
                var job = new dev.arcaneclient.scan.ChunkScanner().begin(client.level,
                    client.level.getChunk(chunk.x, chunk.z), 0, 10, false, true, false, false);
                while (!job.isComplete()) job.step(256);
                require(job.worldObservations().size() == 8, "ESP scanner must collect all four stages above and below deepslate");
                require(job.grownCount() == 6, "Only the two full clusters add to the four other grown plants");
                ArcaneClient.config().enabled = false;
                ArcaneClient.config().evidencePoints = false;
                ArcaneClient.config().amethystEsp = true;
                ArcaneClient.config().amethystEspRange = 384;
                ArcaneClient.config().cleanCapture = false;
                ArcaneClient.config().streamerMode = false;
                ArcaneClient.engine().settingsChanged(client);
            });
            context.waitFor(client -> ArcaneClient.engine().amethystStageCount(0) == 2
                && ArcaneClient.engine().amethystStageCount(1) == 2
                && ArcaneClient.engine().amethystStageCount(2) == 2
                && ArcaneClient.engine().amethystStageCount(3) == 2, 5000);
            context.waitFor(client -> dev.arcaneclient.render.TraceRenderer.renderedAmethystCount() == 8, 5000);
            context.runOnClient(client -> ArcaneClient.config().amethystEsp = false);
            ArcaneClient.LOGGER.info("[QA] Amethyst passed: raw received counts 2/2/2/2, eight scanned positions and eight rendered markers; both heights, finder disabled");
            ArcaneClient.LOGGER.info("[QA] Count-based finder passed: five plant types, exact count threshold, 100% sensitivity, removal refresh, block-entity exclusion and optional tracers");
        }
    }
    private static boolean flagged(ChunkPos chunk) {
        return ArcaneClient.engine().markers().stream().anyMatch(marker -> marker.chunkX() == chunk.x && marker.chunkZ() == chunk.z);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
