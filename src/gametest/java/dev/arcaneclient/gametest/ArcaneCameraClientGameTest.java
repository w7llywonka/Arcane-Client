package dev.arcaneclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.mixin.MinecraftClientAccessor;
import dev.arcaneclient.render.EspRenderer;
import dev.arcaneclient.render.WorldIntelRenderer;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.scan.ChunkScanner;
import dev.arcaneclient.utility.ElytraAssistController;
import dev.arcaneclient.utility.RelogController;

import java.util.ArrayList;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("UnstableApiUsage")
public final class ArcaneCameraClientGameTest implements FabricClientGameTest {
    private static final double POSITION_EPSILON = 0.05;

    @Override
    public void runTest(ClientGameTestContext context) {
        // Keep software-rendered CI cheap while the integrated server generates its spawn chunks.
        context.getInput().resizeWindow(640, 360);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(20);
            prepare(context);
            testRapidCameraToggles(context);
            testFreecam(context);
            testFreelook(context);
            testModeSwitching(context);
            testElytraAssist(context);
            testStorageExcludedFromScanner(context);
            testStorageEspLayerSwitch(context);
            testWorldIntelSearch(context);
            testRelogSingleplayerGuard(context);
            testInterface(context);
            testScannerThroughput(context);
        } finally {
            context.runOnClient(client -> {
                FreecamController.disable(client);
                FreelookController.disable(client);
            });
        }
    }

    private static void testScannerThroughput(ClientGameTestContext context) {
        ScannerBenchmark benchmark = context.computeOnClient(client -> {
            require(client.level != null && client.player != null, "scanner benchmark needs a loaded world");
            ChunkScanner scanner = new ChunkScanner();
            ChunkPos center = client.player.chunkPosition();
            int observerSectionY = SectionPos.blockToSectionCoord(client.player.getBlockY());
            int chunks = 0;
            int visitedBlocks = 0;
            int availableBlocks = 0;
            long started = System.nanoTime();
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dx = -2; dx <= 2; ++dx) {
                    LevelChunk chunk = client.level.getChunkSource().getChunk(center.x + dx, center.z + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    ChunkScanner.ChunkScanJob job = scanner.begin(client.level, chunk, 0L, observerSectionY, false);
                    while (!job.isComplete()) {
                        visitedBlocks += job.step(65_536);
                    }
                    availableBlocks += chunk.getSections().length * 4_096;
                    ++chunks;
                }
            }
            return new ScannerBenchmark(chunks, visitedBlocks, availableBlocks, System.nanoTime() - started);
        });
        require(benchmark.chunks >= 9, "not enough loaded chunks for scanner benchmark");
        require(benchmark.visitedBlocks * 2 < benchmark.availableBlocks, "palette prefilter failed to reject most ordinary terrain");
        require(benchmark.elapsedNanos < 750_000_000L, "scanner benchmark exceeded 750 ms for " + benchmark.chunks + " chunks");
        ArcaneClient.LOGGER.info(
            "[QA] Scanner benchmark: {} chunks, {} of {} blocks visited in {} ms",
            benchmark.chunks,
            benchmark.visitedBlocks,
            benchmark.availableBlocks,
            benchmark.elapsedNanos / 1_000_000.0
        );
    }

    private static void testStorageExcludedFromScanner(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting storage-free Chunk Finder test");
        context.runOnClient(client -> {
            require(client.level != null && client.player != null, "storage exclusion test needs a loaded world");
            LevelChunk chunk = client.level.getChunk(client.player.chunkPosition().x, client.player.chunkPosition().z);
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int y = client.level.getMinY(); y <= client.level.getMaxY() && positions.size() < 5; y++) {
                for (int z = 1; z < 15 && positions.size() < 5; z++) {
                    for (int x = 1; x < 15 && positions.size() < 5; x++) {
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                        if (client.level.getBlockState(pos).isAir()) positions.add(pos);
                    }
                }
            }
            require(positions.size() == 5, "storage exclusion test could not find five air blocks");

            ChunkScanner scanner = new ChunkScanner();
            int observerSectionY = SectionPos.blockToSectionCoord(client.player.getBlockY());
            ChunkScanner.ChunkScanJob baselineJob = scanner.begin(client.level, chunk, 0L, observerSectionY, false);
            while (!baselineJob.isComplete()) baselineJob.step(65_536);
            int baselineScore = baselineJob.result().score();
            BlockState[] previous = new BlockState[positions.size()];
            Block[] storage = new Block[]{Blocks.CHEST, Blocks.BARREL, Blocks.HOPPER, Blocks.SHULKER_BOX, Blocks.FURNACE};
            try {
                for (int index = 0; index < positions.size(); index++) {
                    previous[index] = client.level.getBlockState(positions.get(index));
                    client.level.setBlock(positions.get(index), storage[index].defaultBlockState(), Block.UPDATE_ALL);
                }
                ChunkScanner.ChunkScanJob storageJob = scanner.begin(client.level, chunk, 1L, observerSectionY, false);
                while (!storageJob.isComplete()) storageJob.step(65_536);
                require(storageJob.result().score() == baselineScore,
                    "storage-only blocks changed the Chunk Finder score");
                require(storageJob.result().reasons().stream().noneMatch(reason -> {
                    String lower = reason.toLowerCase(java.util.Locale.ROOT);
                    return lower.contains("storage") || lower.contains("chest") || lower.contains("barrel")
                        || lower.contains("hopper") || lower.contains("shulker") || lower.contains("furnace");
                }), "storage-only blocks leaked into Chunk Finder reasons");
            } finally {
                for (int index = 0; index < positions.size(); index++) {
                    if (previous[index] != null) {
                        client.level.setBlock(positions.get(index), previous[index], Block.UPDATE_ALL);
                    }
                }
            }
        });
        ArcaneClient.LOGGER.info("[QA] Storage-free Chunk Finder test passed");
    }

    private static void testStorageEspLayerSwitch(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Storage ESP render-buffer regression test");
        StorageEspState previous = context.computeOnClient(client -> {
            require(client.level != null && client.player != null, "Storage ESP test needs a loaded world");
            BlockPos origin = client.player.blockPosition();
            BlockPos target = null;
            for (int y = 1; y <= 5 && target == null; y++) {
                for (int z = -3; z <= 3 && target == null; z++) {
                    for (int x = -3; x <= 3; x++) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (client.level.getBlockState(candidate).isAir()) {
                            target = candidate.immutable();
                            break;
                        }
                    }
                }
            }
            require(target != null, "Storage ESP test could not find a nearby air block");
            StorageEspState state = new StorageEspState(
                target,
                client.level.getBlockState(target),
                ArcaneClient.config().esp,
                ArcaneClient.config().performanceProfile
            );
            client.level.setBlock(target, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            ArcaneClient.config().esp = true;
            ArcaneClient.config().performanceProfile = 2;
            return state;
        });
        try {
            context.waitTicks(5);
            context.runOnClient(client -> require(
                EspRenderer.targetCount() > 0,
                "Storage ESP did not discover the regression-test chest"
            ));
        } finally {
            context.runOnClient(client -> {
                if (client.level != null) {
                    client.level.setBlock(previous.pos(), previous.state(), Block.UPDATE_ALL);
                }
                ArcaneClient.config().esp = previous.esp();
                ArcaneClient.config().performanceProfile = previous.performanceProfile();
            });
            context.waitTicks(2);
        }
        ArcaneClient.LOGGER.info("[QA] Storage ESP render-buffer regression test passed");
    }

    private static void testWorldIntelSearch(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting bounded Search index test");
        SearchState previous = context.computeOnClient(client -> {
            require(client.level != null && client.player != null, "Search test needs a loaded world");
            BlockPos origin = client.player.blockPosition();
            BlockPos target = null;
            for (int y = 1; y <= 6 && target == null; y++) {
                for (int z = -4; z <= 4 && target == null; z++) {
                    for (int x = -4; x <= 4; x++) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (client.level.getBlockState(candidate).isAir()) {
                            target = candidate.immutable();
                            break;
                        }
                    }
                }
            }
            require(target != null, "Search test could not find a nearby air block");
            SearchState state = new SearchState(
                target,
                client.level.getBlockState(target),
                ArcaneClient.config().searchEsp,
                ArcaneClient.config().portalEsp,
                ArcaneClient.config().searchEspRange
            );
            client.level.setBlock(target, Blocks.BEACON.defaultBlockState(), Block.UPDATE_ALL);
            ArcaneClient.config().searchEsp = true;
            ArcaneClient.config().portalEsp = false;
            ArcaneClient.config().searchEspRange = 16;
            WorldIntelRenderer.onWorldChange(client.level);
            WorldIntelRenderer.onChunkLoad(client.level, client.level.getChunk(target.getX() >> 4, target.getZ() >> 4));
            return state;
        });
        try {
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(WorldIntelRenderer.searchTargetCount() > 0, "Search index did not discover the beacon");
                require(WorldIntelRenderer.queuedChunkCount() == 0, "Search index did not drain its bounded queue");
            });
        } finally {
            context.runOnClient(client -> {
                if (client.level != null) client.level.setBlock(previous.pos(), previous.state(), Block.UPDATE_ALL);
                ArcaneClient.config().searchEsp = previous.searchEsp();
                ArcaneClient.config().portalEsp = previous.portalEsp();
                ArcaneClient.config().searchEspRange = previous.searchRange();
                WorldIntelRenderer.onWorldChange(client.level);
            });
            context.waitTicks(2);
        }
        ArcaneClient.LOGGER.info("[QA] Bounded Search index test passed");
    }

    private static void testRapidCameraToggles(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting immediate camera input test");
        TestInput input = context.getInput();

        input.pressKey(ArcaneClient.keybinds().freecam());
        require(FreecamController.isActive(), "Freecam raw press did not activate immediately");
        input.pressKey(ArcaneClient.keybinds().freecam());
        require(!FreecamController.isActive(), "Freecam rapid second press was lost");

        context.runOnClient(client -> {
            ArcaneClient.keybinds().freelook().setBoundKey(
                InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F4)
            );
            KeyMapping.resetMapping();
        });
        input.pressKey(GLFW.GLFW_KEY_F4);
        require(FreelookController.isActive(), "Freelook raw press did not activate immediately");
        input.pressKey(GLFW.GLFW_KEY_F4);
        require(!FreelookController.isActive(), "Freelook rapid second press was lost");
        context.runOnClient(client -> {
            ArcaneClient.keybinds().freelook().setBoundKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
        });
        ArcaneClient.LOGGER.info("[QA] Immediate camera input test passed");
    }

    private static void prepare(ClientGameTestContext context) {
        context.runOnClient(client -> {
            require(client.player != null, "singleplayer test did not create a player");
            require(client.level != null, "singleplayer test did not create a client world");
            client.options.hideGui = false;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.setScreen(null);
            FreecamController.disable(client);
            FreelookController.disable(client);
        });
        context.waitTicks(2);
        assertHudAvailable(context, "baseline");
        context.takeScreenshot("arcane-baseline-hotbar");
    }

    private static void testFreecam(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Freecam end-to-end test");
        Snapshot bodyStart = snapshot(context);
        boolean previousChunkCulling = context.computeOnClient(client -> client.smartCull);
        context.runOnClient(FreecamController::enable);
        context.waitTicks(2);

        Snapshot enabled = snapshot(context);
        require(FreecamController.isActive(), "Freecam did not activate");
        require(enabled.cameraIsPlayer(), "Freecam replaced the player camera entity and broke the hotbar");
        require(FreecamController.hasVisualBody(), "Freecam did not create the visible stationary body");
        context.runOnClient(client -> require(
            !client.smartCull,
            "Freecam left underground section occlusion enabled"
        ));
        context.runOnClient(client -> {
            Entity visualBody = FreecamController.visualBodyEntity();
            require(visualBody instanceof AbstractClientPlayer, "Freecam visual body was not player-rendered");
            require(
                ((AbstractClientPlayer) visualBody).getSkin().equals(client.player.getSkin()),
                "Freecam visual body did not preserve the authenticated player skin"
            );

            Vec3 groundedPosition = client.player.position();
            client.player.setPos(
                groundedPosition.x,
                groundedPosition.y + 2.0,
                groundedPosition.z
            );
            client.player.setOldPosAndRot();
            FreecamController.tick(client);
            require(
                visualBody.position().distanceTo(client.player.position()) < POSITION_EPSILON,
                "Freecam visual body hovered at the activation height instead of following the airborne player"
            );
            client.player.setPos(groundedPosition);
            client.player.setOldPosAndRot();
            FreecamController.tick(client);
        });
        require(enabled.perspective() == CameraType.FIRST_PERSON, "Freecam did not retain first-person HUD rendering");
        assertHudAvailable(context, "Freecam enabled");

        TestInput input = context.getInput();
        input.holdKeyFor(key(context, options -> options.keyUp), 14);
        context.waitTicks(2);
        Snapshot forward = snapshot(context);
        require(forward.cameraPos().distanceTo(enabled.cameraPos()) > 2.0, "Freecam camera did not move with forward input");
        require(forward.playerPos().distanceTo(bodyStart.playerPos()) < POSITION_EPSILON, "Freecam moved the real player body");

        input.holdKeyFor(key(context, options -> options.keyJump), 8);
        context.waitTicks(2);
        Snapshot raised = snapshot(context);
        require(raised.cameraPos().y > forward.cameraPos().y + 0.5, "Freecam did not rise with jump input");
        require(raised.playerPos().distanceTo(bodyStart.playerPos()) < POSITION_EPSILON, "vertical Freecam input moved the real body");

        float playerYaw = raised.playerYaw();
        float playerPitch = raised.playerPitch();
        context.runOnClient(client -> client.player.turn(400.0, 300.0));
        context.waitTick();
        Snapshot positiveLook = snapshot(context);
        context.runOnClient(client -> client.player.turn(-800.0, -600.0));
        context.waitTick();
        Snapshot negativeLook = snapshot(context);
        require(positiveLook.cameraYaw() > raised.cameraYaw() + 30.0f, "Freecam did not rotate right");
        require(negativeLook.cameraYaw() < positiveLook.cameraYaw() - 60.0f, "Freecam did not rotate left");
        require(positiveLook.cameraPitch() > raised.cameraPitch() + 20.0f, "Freecam did not look downward");
        require(negativeLook.cameraPitch() < positiveLook.cameraPitch() - 40.0f, "Freecam did not look upward");
        require(close(negativeLook.playerYaw(), playerYaw) && close(negativeLook.playerPitch(), playerPitch), "Freecam rotated the real player's head");

        faceBody(context);
        context.waitTicks(2);
        assertRenderedCameraFacesBody(context);
        assertHudAvailable(context, "Freecam body view");
        context.takeScreenshot("arcane-freecam-body-hotbar");
        Snapshot bodyView = snapshot(context);
        require(FreecamController.hasVisualBody(), "Freecam visual body disappeared before capture");
        ArcaneClient.LOGGER.info("[QA] Freecam visual body captured at {} from camera {}", bodyView.playerPos(), bodyView.cameraPos());

        context.runOnClient(client -> {
            client.gameRenderer.pick(1.0f);
            require(!FreecamController.isVisualBody(client.crosshairPickEntity), "Freecam visual body became a crosshair target");
            Entity visualBody = FreecamController.visualBodyEntity();
            require(visualBody != null, "Freecam visual body disappeared before interaction test");
            require(!visualBody.isPickable(), "Freecam visual body was hittable");
            require(!visualBody.isAttackable(), "Freecam visual body was attackable");
            require(!visualBody.canInteractWithLevel(), "Freecam visual body was interactable");
            client.gameMode.attack(client.player, visualBody);
            require(client.getCameraEntity() == client.player, "Freecam lost the player camera before interaction test");
        });
        assertGoldenAppleUse(context, "Freecam");
        context.waitTicks(3);
        Snapshot afterInvalidAttack = snapshot(context);
        require(afterInvalidAttack.connectionOpen(), "client-only visual body attack escaped to the integrated server");
        require(FreecamController.isActive(), "Freecam was lost after guarded camera interaction");

        context.runOnClient(FreecamController::disable);
        context.waitTicks(2);
        Snapshot disabled = snapshot(context);
        require(!FreecamController.isActive(), "Freecam did not disable");
        require(!FreecamController.hasVisualBody(), "Freecam left its visual body in the world after disabling");
        require(disabled.cameraIsPlayer(), "Freecam did not restore the player camera");
        require(disabled.perspective() == CameraType.FIRST_PERSON, "Freecam did not restore the previous perspective");
        context.runOnClient(client -> require(
            client.smartCull == previousChunkCulling,
            "Freecam did not restore the previous chunk-culling state"
        ));
        assertHudAvailable(context, "Freecam disabled");
        ArcaneClient.LOGGER.info("[QA] Freecam end-to-end test passed");
    }

    private static void testFreelook(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Freelook end-to-end test");
        Snapshot bodyStart = snapshot(context);
        ArcaneClient.config().freelookThroughWalls = true;
        context.runOnClient(FreelookController::enable);
        context.waitTicks(2);

        Snapshot enabled = snapshot(context);
        require(FreelookController.isActive(), "Freelook did not activate");
        require(FreelookController.ignoresCameraCollision(), "Freelook through-walls setting did not bypass camera collision");
        ArcaneClient.config().freelookThroughWalls = false;
        require(!FreelookController.ignoresCameraCollision(), "Freelook camera collision did not react to its module setting immediately");
        require(!FreecamController.hasVisualBody(), "Freelook created a duplicate player instead of rendering the real skin");
        require(enabled.cameraIsPlayer(), "Freelook replaced the player camera entity and broke movement/HUD state");
        require(enabled.perspective() == CameraType.THIRD_PERSON_BACK, "Freelook did not use third-person orbit rendering");
        assertAnchored(enabled, "Freelook activation");
        assertHudAvailable(context, "Freelook enabled");

        float playerYaw = enabled.playerYaw();
        float playerPitch = enabled.playerPitch();
        context.runOnClient(client -> client.player.turn(500.0, 300.0));
        context.waitTick();
        Snapshot positiveLook = snapshot(context);
        context.runOnClient(client -> client.player.turn(-1000.0, -600.0));
        context.waitTick();
        Snapshot negativeLook = snapshot(context);
        require(positiveLook.cameraYaw() > enabled.cameraYaw() + 40.0f, "Freelook did not orbit right");
        require(negativeLook.cameraYaw() < positiveLook.cameraYaw() - 80.0f, "Freelook did not orbit left");
        require(positiveLook.cameraPitch() > enabled.cameraPitch() + 20.0f, "Freelook did not look downward");
        require(negativeLook.cameraPitch() < positiveLook.cameraPitch() - 40.0f, "Freelook did not look upward");
        assertRenderedAngles(positiveLook, "Freelook positive orbit");
        assertRenderedAngles(negativeLook, "Freelook negative orbit");
        require(close(negativeLook.playerYaw(), playerYaw) && close(negativeLook.playerPitch(), playerPitch), "Freelook rotated the real player's head");
        assertAnchored(negativeLook, "Freelook mouse orbit");

        context.getInput().holdKeyFor(key(context, options -> options.keyUp), 20);
        context.waitTicks(2);
        Snapshot moved = snapshot(context);
        require(
            moved.playerPos().distanceTo(bodyStart.playerPos()) > 0.15,
            "Freelook froze normal player movement"
        );
        assertAnchored(moved, "Freelook following normal player movement");
        assertHudAvailable(context, "Freelook anchored");
        context.takeScreenshot("arcane-freelook-orbit-hotbar");

        context.runOnClient(client -> {
            client.gameRenderer.pick(1.0f);
            require(client.getCameraEntity() == client.player, "Freelook lost the real player camera before interaction test");
            require(client.crosshairPickEntity != client.player, "Freelook targeted the local player");
        });
        assertFreelookAutoTool(context);
        assertGoldenAppleUse(context, "Freelook");
        context.waitTicks(3);
        Snapshot afterInvalidAttack = snapshot(context);
        require(afterInvalidAttack.connectionOpen(), "Freelook interaction handling disconnected the client");
        require(FreelookController.isActive(), "Freelook was lost after guarded camera interaction");

        context.runOnClient(FreelookController::disable);
        context.waitTicks(2);
        Snapshot disabled = snapshot(context);
        require(!FreelookController.isActive(), "Freelook did not disable");
        require(!FreecamController.hasVisualBody(), "Freelook left a duplicate player in the world after disabling");
        require(
            disabled.playerPos().distanceTo(moved.playerPos()) < 0.25,
            "Freelook snapped the real player back when disabled"
        );
        require(disabled.cameraIsPlayer(), "Freelook did not restore the player camera");
        require(disabled.perspective() == CameraType.FIRST_PERSON, "Freelook did not restore the previous perspective");
        assertHudAvailable(context, "Freelook disabled");
        ArcaneClient.LOGGER.info("[QA] Freelook end-to-end test passed");
    }

    private static void assertFreelookAutoTool(ClientGameTestContext context) {
        context.runOnClient(client -> {
            require(client.player != null && client.level != null, "Freelook Auto Tool test lost its world");
            boolean previousAutoTool = ArcaneClient.config().autoTool;
            boolean previousPreserveDurability = ArcaneClient.config().autoToolPreserveDurability;
            float previousYaw = client.player.getYRot();
            float previousPitch = client.player.getXRot();
            int previousSlot = client.player.getInventory().getSelectedSlot();
            ItemStack[] previousStacks = new ItemStack[5];
            for (int slot = 0; slot < previousStacks.length; slot++) {
                previousStacks[slot] = client.player.getInventory().getItem(slot).copy();
            }
            try {
                ArcaneClient.config().autoTool = true;
                ArcaneClient.config().autoToolPreserveDurability = false;
                client.player.getInventory().setItem(0, new ItemStack(Items.STICK));
                client.player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
                client.player.getInventory().setItem(2, new ItemStack(Items.DIAMOND_SHOVEL));
                client.player.getInventory().setItem(3, new ItemStack(Items.DIAMOND_AXE));
                client.player.getInventory().setItem(4, new ItemStack(Items.SHEARS));
                client.player.getInventory().setSelectedSlot(0);
                client.player.setXRot(89.9f);

                HitResult target = DetachedCameraInteraction.itemUseTarget(client);
                require(target instanceof BlockHitResult && target.getType() == HitResult.Type.BLOCK,
                    "Freelook Auto Tool test could not aim at the floor");
                client.options.keyAttack.setDown(true);
                FreelookController.tick(client);
                require(client.player.getInventory().getSelectedSlot() != 0,
                    "Freelook mining bypassed Auto Tool selection");

                client.options.keyAttack.setDown(false);
                FreelookController.tick(client);
                require(client.player.getInventory().getSelectedSlot() == 0,
                    "Freelook Auto Tool did not restore the original slot");
            } finally {
                client.options.keyAttack.setDown(false);
                DetachedCameraInteraction.stopMining(client);
                for (int slot = 0; slot < previousStacks.length; slot++) {
                    client.player.getInventory().setItem(slot, previousStacks[slot]);
                }
                client.player.getInventory().setSelectedSlot(previousSlot);
                client.player.setYRot(previousYaw);
                client.player.setXRot(previousPitch);
                ArcaneClient.config().autoTool = previousAutoTool;
                ArcaneClient.config().autoToolPreserveDurability = previousPreserveDurability;
            }
        });
    }

    private static void assertGoldenAppleUse(ClientGameTestContext context, String stage) {
        context.runOnClient(client -> {
            require(client.player != null, stage + " item-use test lost the player");
            ItemStack previous = client.player.getInventory().getSelectedItem().copy();
            try {
                client.player.getInventory().setSelectedItem(new ItemStack(Items.GOLDEN_APPLE));
                MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
                accessor.arcaneclient$setItemUseCooldown(0);
                accessor.arcaneclient$doItemUse();
                require(accessor.arcaneclient$getItemUseCooldown() == 4, stage + " canceled vanilla item use");
                require(client.player.isUsingItem(), stage + " did not start using a golden apple");
            } finally {
                client.player.releaseUsingItem();
                client.player.getInventory().setSelectedItem(previous);
            }
        });
    }

    private static void testModeSwitching(ClientGameTestContext context) {
        context.runOnClient(FreecamController::enable);
        context.waitTick();
        context.runOnClient(FreelookController::enable);
        context.waitTick();
        require(!FreecamController.isActive() && FreelookController.isActive(), "switching Freecam to Freelook left both modes active");
        context.runOnClient(FreecamController::enable);
        context.waitTick();
        require(FreecamController.isActive() && !FreelookController.isActive(), "switching Freelook to Freecam left both modes active");
        context.runOnClient(FreecamController::disable);
        context.waitTick();
    }

    private static void testElytraAssist(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Elytra Assist rocket-use test");
        context.runOnClient(client -> {
            require(client.player != null && client.level != null, "Elytra Assist test lost its world");
            boolean previousEnabled = ArcaneClient.config().elytraAssist;
            boolean previousSmart = ArcaneClient.config().elytraAssistSmartConservation;
            int previousDelay = ArcaneClient.config().elytraAssistDelayTicks;
            int previousThreshold = ArcaneClient.config().elytraAssistBoostBelow;
            int previousSlot = client.player.getInventory().getSelectedSlot();
            ItemStack previousSlotZero = client.player.getInventory().getItem(0).copy();
            ItemStack previousSlotTwo = client.player.getInventory().getItem(2).copy();
            ItemStack previousOffhand = client.player.getOffhandItem().copy();
            Vec3 previousVelocity = client.player.getDeltaMovement();
            try {
                ArcaneClient.config().elytraAssist = true;
                ArcaneClient.config().elytraAssistSmartConservation = false;
                ArcaneClient.config().elytraAssistDelayTicks = 40;
                ArcaneClient.config().elytraAssistBoostBelow = 24;
                client.player.getInventory().setItem(0, new ItemStack(Items.STICK));
                client.player.getInventory().setItem(2, new ItemStack(Items.FIREWORK_ROCKET, 8));
                client.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                client.player.getInventory().setSelectedSlot(0);
                client.player.startFallFlying();
                ElytraAssistController.reset();

                ElytraAssistController.tick(client);
                require(ElytraAssistController.cooldownTicks() == 40,
                    "Elytra Assist did not use a hotbar rocket while gliding");
                require(client.player.getInventory().getSelectedSlot() == 0,
                    "Elytra Assist did not restore the selected slot after boosting");

                ElytraAssistController.tick(client);
                require(ElytraAssistController.cooldownTicks() == 39,
                    "Elytra Assist ignored its configured boost delay");

                ElytraAssistController.reset();
                ArcaneClient.config().elytraAssistSmartConservation = true;
                client.player.setDeltaMovement(2.0, 0.0, 0.0);
                ElytraAssistController.tick(client);
                require(ElytraAssistController.cooldownTicks() == 0,
                    "Elytra Assist smart conservation spent a rocket above its speed threshold");
            } finally {
                ElytraAssistController.reset();
                client.player.stopFallFlying();
                client.player.setDeltaMovement(previousVelocity);
                client.player.getInventory().setItem(0, previousSlotZero);
                client.player.getInventory().setItem(2, previousSlotTwo);
                client.player.setItemInHand(InteractionHand.OFF_HAND, previousOffhand);
                client.player.getInventory().setSelectedSlot(previousSlot);
                ArcaneClient.config().elytraAssist = previousEnabled;
                ArcaneClient.config().elytraAssistSmartConservation = previousSmart;
                ArcaneClient.config().elytraAssistDelayTicks = previousDelay;
                ArcaneClient.config().elytraAssistBoostBelow = previousThreshold;
            }
        });
        ArcaneClient.LOGGER.info("[QA] Elytra Assist rocket-use test passed");
    }

    private static void testInterface(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Arcane interface visual test");
        context.runOnClient(client -> client.setScreen(new ArcaneSettingsScreen(null)));
        context.waitTicks(4);
        context.runOnClient(client -> require(
            client.screen instanceof ArcaneSettingsScreen,
            "Arcane settings screen did not remain open"
        ));
        context.takeScreenshot("arcane-settings-interface");
        context.runOnClient(client -> client.setScreen(null));
        context.waitTicks(2);
        ArcaneClient.LOGGER.info("[QA] Arcane interface visual test passed");
    }

    private static void testRelogSingleplayerGuard(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Relog singleplayer guard test");
        context.runOnClient(client -> require(
            RelogController.relog(client) == RelogController.Result.MULTIPLAYER_ONLY,
            "Relog must not disconnect an integrated singleplayer world"
        ));
        context.runOnClient(client -> require(
            !RelogController.isPending(),
            "Relog must not schedule a reconnect from an integrated singleplayer world"
        ));
        context.waitTicks(2);
        context.runOnClient(client -> require(
            client.level != null && client.player != null && client.isLocalServer(),
            "Relog singleplayer guard changed the active world"
        ));
        ArcaneClient.LOGGER.info("[QA] Relog singleplayer guard test passed");
    }

    private static void faceBody(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Vec3 bodyEye = client.player.getEyePosition();
            net.minecraft.client.Camera renderedCamera = client.gameRenderer.getMainCamera();
            Vec3 offset = bodyEye.subtract(renderedCamera.position());
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-offset.y, horizontal));
            FreecamController.changeLookDirection(
                (yaw - FreecamController.cameraYaw()) / 0.15,
                (pitch - FreecamController.cameraPitch()) / 0.15
            );
            CameraType perspective = client.options.getCameraType();
            client.gameRenderer.getMainCamera().setup(
                client.level,
                client.player,
                !perspective.isFirstPerson(),
                perspective.isMirrored(),
                1.0f
            );
        });
    }

    private static void assertRenderedCameraFacesBody(ClientGameTestContext context) {
        context.runOnClient(client -> {
            net.minecraft.client.Camera renderedCamera = client.gameRenderer.getMainCamera();
            double alignment = renderedBodyAlignment(client);
            ArcaneClient.LOGGER.info(
                "[QA] Freecam pose yaw {} pitch {}; rendered camera at {} yaw {} pitch {}; body alignment {}",
                FreecamController.cameraYaw(),
                FreecamController.cameraPitch(),
                renderedCamera.position(),
                renderedCamera.yRot(),
                renderedCamera.xRot(),
                alignment
            );
            require(alignment > 0.98, "Freecam screenshot camera was not aimed at the stationary body");
        });
    }

    private static double renderedBodyAlignment(Minecraft client) {
        net.minecraft.client.Camera renderedCamera = client.gameRenderer.getMainCamera();
        Vec3 towardBody = client.player.getEyePosition().subtract(renderedCamera.position()).normalize();
        Vec3 renderedLook = Vec3.directionFromRotation(renderedCamera.xRot(), renderedCamera.yRot()).normalize();
        return renderedLook.dot(towardBody);
    }

    private static void assertRenderedAngles(Snapshot snapshot, String stage) {
        require(close(snapshot.renderedYaw(), snapshot.cameraYaw()), stage + " rendered the wrong yaw");
        require(close(snapshot.renderedPitch(), snapshot.cameraPitch()), stage + " rendered the wrong pitch");
    }

    private static void assertAnchored(Snapshot snapshot, String stage) {
        double orbitDistance = snapshot.cameraPos().distanceTo(snapshot.playerPos());
        require(orbitDistance > 1.0 && orbitDistance < 6.0, stage + " was not a player-anchored third-person orbit");
    }

    private static void assertHudAvailable(ClientGameTestContext context, String stage) {
        context.runOnClient(client -> {
            require(!client.options.hideGui, stage + " hid the HUD/hotbar");
            require(client.screen == null, stage + " left a screen covering the hotbar");
            require(client.getCameraEntity() instanceof Player, stage + " made InGameHud.getCameraPlayer return null");
            require(client.getCameraEntity() == client.player, stage + " detached HUD state from the real inventory");
        });
    }


    private static KeyMapping key(ClientGameTestContext context, Function<Options, KeyMapping> getter) {
        return context.computeOnClient(client -> getter.apply(client.options));
    }

    private static Snapshot snapshot(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            require(client.player != null, "player disappeared during camera test");
            require(client.level != null, "world disappeared during camera test");
            Entity camera = client.getCameraEntity();
            require(camera != null, "camera entity disappeared during camera test");
            net.minecraft.client.Camera renderedCamera = client.gameRenderer.getMainCamera();
            float controllerYaw = FreecamController.isActive()
                ? FreecamController.cameraYaw()
                : FreelookController.isActive() ? FreelookController.cameraYaw() : camera.getYRot();
            float controllerPitch = FreecamController.isActive()
                ? FreecamController.cameraPitch()
                : FreelookController.isActive() ? FreelookController.cameraPitch() : camera.getXRot();
            boolean open = client.getConnection() != null && client.getConnection().getConnection().isConnected();
            return new Snapshot(
                renderedCamera.position(),
                new Vec3(client.player.getX(), client.player.getY(), client.player.getZ()),
                controllerYaw,
                controllerPitch,
                renderedCamera.yRot(),
                renderedCamera.xRot(),
                client.player.getYRot(),
                client.player.getXRot(),
                client.player.getId(),
                camera == client.player,
                client.options.getCameraType(),
                open
            );
        });
    }

    private static boolean close(float left, float right) {
        return Math.abs(left - right) < 0.001f;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Snapshot(
        Vec3 cameraPos,
        Vec3 playerPos,
        float cameraYaw,
        float cameraPitch,
        float renderedYaw,
        float renderedPitch,
        float playerYaw,
        float playerPitch,
        int playerId,
        boolean cameraIsPlayer,
        CameraType perspective,
        boolean connectionOpen
    ) {
    }

    private record ScannerBenchmark(int chunks, int visitedBlocks, int availableBlocks, long elapsedNanos) {
    }

    private record StorageEspState(
        BlockPos pos,
        BlockState state,
        boolean esp,
        int performanceProfile
    ) {
    }

    private record SearchState(BlockPos pos, BlockState state, boolean searchEsp, boolean portalEsp, int searchRange) {
    }
}
