package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.mixin.MinecraftClientAccessor;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.scan.ChunkScanner;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;
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
            require(client.world != null && client.player != null, "scanner benchmark needs a loaded world");
            ChunkScanner scanner = new ChunkScanner();
            ChunkPos center = client.player.getChunkPos();
            int observerSectionY = ChunkSectionPos.getSectionCoord(client.player.getBlockY());
            int chunks = 0;
            int visitedBlocks = 0;
            int availableBlocks = 0;
            long started = System.nanoTime();
            for (int dz = -2; dz <= 2; ++dz) {
                for (int dx = -2; dx <= 2; ++dx) {
                    WorldChunk chunk = client.world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    ChunkScanner.ChunkScanJob job = scanner.begin(client.world, chunk, 0L, observerSectionY, false);
                    while (!job.isComplete()) {
                        visitedBlocks += job.step(65_536);
                    }
                    availableBlocks += chunk.getSectionArray().length * 4_096;
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

    private static void testRapidCameraToggles(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting immediate camera input test");
        TestInput input = context.getInput();

        input.pressKey(ArcaneClient.keybinds().freecam());
        require(FreecamController.isActive(), "Freecam raw press did not activate immediately");
        input.pressKey(ArcaneClient.keybinds().freecam());
        require(!FreecamController.isActive(), "Freecam rapid second press was lost");

        context.runOnClient(client -> {
            ArcaneClient.keybinds().freelook().setBoundKey(
                InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F4)
            );
            KeyBinding.updateKeysByCode();
        });
        input.pressKey(GLFW.GLFW_KEY_F4);
        require(FreelookController.isActive(), "Freelook raw press did not activate immediately");
        input.pressKey(GLFW.GLFW_KEY_F4);
        require(!FreelookController.isActive(), "Freelook rapid second press was lost");
        context.runOnClient(client -> {
            ArcaneClient.keybinds().freelook().setBoundKey(InputUtil.UNKNOWN_KEY);
            KeyBinding.updateKeysByCode();
        });
        ArcaneClient.LOGGER.info("[QA] Immediate camera input test passed");
    }

    private static void prepare(ClientGameTestContext context) {
        context.runOnClient(client -> {
            require(client.player != null, "singleplayer test did not create a player");
            require(client.world != null, "singleplayer test did not create a client world");
            client.options.hudHidden = false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
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
        boolean previousChunkCulling = context.computeOnClient(client -> client.chunkCullingEnabled);
        context.runOnClient(FreecamController::enable);
        context.waitTicks(2);

        Snapshot enabled = snapshot(context);
        require(FreecamController.isActive(), "Freecam did not activate");
        require(enabled.cameraIsPlayer(), "Freecam replaced the player camera entity and broke the hotbar");
        require(FreecamController.hasVisualBody(), "Freecam did not create the visible stationary body");
        context.runOnClient(client -> require(
            !client.chunkCullingEnabled,
            "Freecam left underground section occlusion enabled"
        ));
        context.runOnClient(client -> {
            Entity visualBody = FreecamController.visualBodyEntity();
            require(visualBody instanceof AbstractClientPlayerEntity, "Freecam visual body was not player-rendered");
            require(
                ((AbstractClientPlayerEntity) visualBody).getSkin().equals(client.player.getSkin()),
                "Freecam visual body did not preserve the authenticated player skin"
            );

            Vec3d groundedPosition = client.player.getEntityPos();
            client.player.setPosition(
                groundedPosition.x,
                groundedPosition.y + 2.0,
                groundedPosition.z
            );
            client.player.resetPosition();
            FreecamController.tick(client);
            require(
                visualBody.getEntityPos().distanceTo(client.player.getEntityPos()) < POSITION_EPSILON,
                "Freecam visual body hovered at the activation height instead of following the airborne player"
            );
            client.player.setPosition(groundedPosition);
            client.player.resetPosition();
            FreecamController.tick(client);
        });
        require(enabled.perspective() == Perspective.FIRST_PERSON, "Freecam did not retain first-person HUD rendering");
        assertHudAvailable(context, "Freecam enabled");

        TestInput input = context.getInput();
        input.holdKeyFor(key(context, options -> options.forwardKey), 14);
        context.waitTicks(2);
        Snapshot forward = snapshot(context);
        require(forward.cameraPos().distanceTo(enabled.cameraPos()) > 2.0, "Freecam camera did not move with forward input");
        require(forward.playerPos().distanceTo(bodyStart.playerPos()) < POSITION_EPSILON, "Freecam moved the real player body");

        input.holdKeyFor(key(context, options -> options.jumpKey), 8);
        context.waitTicks(2);
        Snapshot raised = snapshot(context);
        require(raised.cameraPos().y > forward.cameraPos().y + 0.5, "Freecam did not rise with jump input");
        require(raised.playerPos().distanceTo(bodyStart.playerPos()) < POSITION_EPSILON, "vertical Freecam input moved the real body");

        float playerYaw = raised.playerYaw();
        float playerPitch = raised.playerPitch();
        context.runOnClient(client -> client.player.changeLookDirection(400.0, 300.0));
        context.waitTick();
        Snapshot positiveLook = snapshot(context);
        context.runOnClient(client -> client.player.changeLookDirection(-800.0, -600.0));
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
            client.gameRenderer.updateCrosshairTarget(1.0f);
            require(!FreecamController.isVisualBody(client.targetedEntity), "Freecam visual body became a crosshair target");
            Entity visualBody = FreecamController.visualBodyEntity();
            require(visualBody != null, "Freecam visual body disappeared before interaction test");
            require(!visualBody.canHit(), "Freecam visual body was hittable");
            require(!visualBody.isAttackable(), "Freecam visual body was attackable");
            require(!visualBody.isInteractable(), "Freecam visual body was interactable");
            client.interactionManager.attackEntity(client.player, visualBody);
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
        require(disabled.perspective() == Perspective.FIRST_PERSON, "Freecam did not restore the previous perspective");
        context.runOnClient(client -> require(
            client.chunkCullingEnabled == previousChunkCulling,
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
        require(enabled.perspective() == Perspective.THIRD_PERSON_BACK, "Freelook did not use third-person orbit rendering");
        assertAnchored(enabled, "Freelook activation");
        assertHudAvailable(context, "Freelook enabled");

        float playerYaw = enabled.playerYaw();
        float playerPitch = enabled.playerPitch();
        context.runOnClient(client -> client.player.changeLookDirection(500.0, 300.0));
        context.waitTick();
        Snapshot positiveLook = snapshot(context);
        context.runOnClient(client -> client.player.changeLookDirection(-1000.0, -600.0));
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

        context.getInput().holdKeyFor(key(context, options -> options.forwardKey), 20);
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
            client.gameRenderer.updateCrosshairTarget(1.0f);
            require(client.getCameraEntity() == client.player, "Freelook lost the real player camera before interaction test");
            require(client.targetedEntity != client.player, "Freelook targeted the local player");
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
        require(disabled.perspective() == Perspective.FIRST_PERSON, "Freelook did not restore the previous perspective");
        assertHudAvailable(context, "Freelook disabled");
        ArcaneClient.LOGGER.info("[QA] Freelook end-to-end test passed");
    }

    private static void assertFreelookAutoTool(ClientGameTestContext context) {
        context.runOnClient(client -> {
            require(client.player != null && client.world != null, "Freelook Auto Tool test lost its world");
            boolean previousAutoTool = ArcaneClient.config().autoTool;
            boolean previousPreserveDurability = ArcaneClient.config().autoToolPreserveDurability;
            float previousYaw = client.player.getYaw();
            float previousPitch = client.player.getPitch();
            int previousSlot = client.player.getInventory().getSelectedSlot();
            ItemStack[] previousStacks = new ItemStack[5];
            for (int slot = 0; slot < previousStacks.length; slot++) {
                previousStacks[slot] = client.player.getInventory().getStack(slot).copy();
            }
            try {
                ArcaneClient.config().autoTool = true;
                ArcaneClient.config().autoToolPreserveDurability = false;
                client.player.getInventory().setStack(0, new ItemStack(Items.STICK));
                client.player.getInventory().setStack(1, new ItemStack(Items.DIAMOND_PICKAXE));
                client.player.getInventory().setStack(2, new ItemStack(Items.DIAMOND_SHOVEL));
                client.player.getInventory().setStack(3, new ItemStack(Items.DIAMOND_AXE));
                client.player.getInventory().setStack(4, new ItemStack(Items.SHEARS));
                client.player.getInventory().setSelectedSlot(0);
                client.player.setPitch(89.9f);

                HitResult target = DetachedCameraInteraction.itemUseTarget(client);
                require(target instanceof BlockHitResult && target.getType() == HitResult.Type.BLOCK,
                    "Freelook Auto Tool test could not aim at the floor");
                client.options.attackKey.setPressed(true);
                FreelookController.tick(client);
                require(client.player.getInventory().getSelectedSlot() != 0,
                    "Freelook mining bypassed Auto Tool selection");

                client.options.attackKey.setPressed(false);
                FreelookController.tick(client);
                require(client.player.getInventory().getSelectedSlot() == 0,
                    "Freelook Auto Tool did not restore the original slot");
            } finally {
                client.options.attackKey.setPressed(false);
                DetachedCameraInteraction.stopMining(client);
                for (int slot = 0; slot < previousStacks.length; slot++) {
                    client.player.getInventory().setStack(slot, previousStacks[slot]);
                }
                client.player.getInventory().setSelectedSlot(previousSlot);
                client.player.setYaw(previousYaw);
                client.player.setPitch(previousPitch);
                ArcaneClient.config().autoTool = previousAutoTool;
                ArcaneClient.config().autoToolPreserveDurability = previousPreserveDurability;
            }
        });
    }

    private static void assertGoldenAppleUse(ClientGameTestContext context, String stage) {
        context.runOnClient(client -> {
            require(client.player != null, stage + " item-use test lost the player");
            ItemStack previous = client.player.getInventory().getSelectedStack().copy();
            try {
                client.player.getInventory().setSelectedStack(new ItemStack(Items.GOLDEN_APPLE));
                MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
                accessor.arcaneclient$setItemUseCooldown(0);
                accessor.arcaneclient$doItemUse();
                require(accessor.arcaneclient$getItemUseCooldown() == 4, stage + " canceled vanilla item use");
                require(client.player.isUsingItem(), stage + " did not start using a golden apple");
            } finally {
                client.player.stopUsingItem();
                client.player.getInventory().setSelectedStack(previous);
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

    private static void testInterface(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Arcane interface visual test");
        context.runOnClient(client -> client.setScreen(new ArcaneSettingsScreen(null)));
        context.waitTicks(4);
        context.runOnClient(client -> require(
            client.currentScreen instanceof ArcaneSettingsScreen,
            "Arcane settings screen did not remain open"
        ));
        context.takeScreenshot("arcane-settings-interface");
        context.runOnClient(client -> client.setScreen(null));
        context.waitTicks(2);
        ArcaneClient.LOGGER.info("[QA] Arcane interface visual test passed");
    }

    private static void faceBody(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Vec3d bodyEye = client.player.getEyePos();
            net.minecraft.client.render.Camera renderedCamera = client.gameRenderer.getCamera();
            Vec3d offset = bodyEye.subtract(renderedCamera.getCameraPos());
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-offset.y, horizontal));
            FreecamController.changeLookDirection(
                (yaw - FreecamController.cameraYaw()) / 0.15,
                (pitch - FreecamController.cameraPitch()) / 0.15
            );
            Perspective perspective = client.options.getPerspective();
            client.gameRenderer.getCamera().update(
                client.world,
                client.player,
                !perspective.isFirstPerson(),
                perspective.isFrontView(),
                1.0f
            );
        });
    }

    private static void assertRenderedCameraFacesBody(ClientGameTestContext context) {
        context.runOnClient(client -> {
            net.minecraft.client.render.Camera renderedCamera = client.gameRenderer.getCamera();
            double alignment = renderedBodyAlignment(client);
            ArcaneClient.LOGGER.info(
                "[QA] Freecam pose yaw {} pitch {}; rendered camera at {} yaw {} pitch {}; body alignment {}",
                FreecamController.cameraYaw(),
                FreecamController.cameraPitch(),
                renderedCamera.getCameraPos(),
                renderedCamera.getYaw(),
                renderedCamera.getPitch(),
                alignment
            );
            require(alignment > 0.98, "Freecam screenshot camera was not aimed at the stationary body");
        });
    }

    private static double renderedBodyAlignment(MinecraftClient client) {
        net.minecraft.client.render.Camera renderedCamera = client.gameRenderer.getCamera();
        Vec3d towardBody = client.player.getEyePos().subtract(renderedCamera.getCameraPos()).normalize();
        Vec3d renderedLook = Vec3d.fromPolar(renderedCamera.getPitch(), renderedCamera.getYaw()).normalize();
        return renderedLook.dotProduct(towardBody);
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
            require(!client.options.hudHidden, stage + " hid the HUD/hotbar");
            require(client.currentScreen == null, stage + " left a screen covering the hotbar");
            require(client.getCameraEntity() instanceof PlayerEntity, stage + " made InGameHud.getCameraPlayer return null");
            require(client.getCameraEntity() == client.player, stage + " detached HUD state from the real inventory");
        });
    }


    private static KeyBinding key(ClientGameTestContext context, Function<GameOptions, KeyBinding> getter) {
        return context.computeOnClient(client -> getter.apply(client.options));
    }

    private static Snapshot snapshot(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            require(client.player != null, "player disappeared during camera test");
            require(client.world != null, "world disappeared during camera test");
            Entity camera = client.getCameraEntity();
            require(camera != null, "camera entity disappeared during camera test");
            net.minecraft.client.render.Camera renderedCamera = client.gameRenderer.getCamera();
            float controllerYaw = FreecamController.isActive()
                ? FreecamController.cameraYaw()
                : FreelookController.isActive() ? FreelookController.cameraYaw() : camera.getYaw();
            float controllerPitch = FreecamController.isActive()
                ? FreecamController.cameraPitch()
                : FreelookController.isActive() ? FreelookController.cameraPitch() : camera.getPitch();
            boolean open = client.getNetworkHandler() != null && client.getNetworkHandler().getConnection().isOpen();
            return new Snapshot(
                renderedCamera.getCameraPos(),
                new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()),
                controllerYaw,
                controllerPitch,
                renderedCamera.getYaw(),
                renderedCamera.getPitch(),
                client.player.getYaw(),
                client.player.getPitch(),
                client.player.getId(),
                camera == client.player,
                client.options.getPerspective(),
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
        Vec3d cameraPos,
        Vec3d playerPos,
        float cameraYaw,
        float cameraPitch,
        float renderedYaw,
        float renderedPitch,
        float playerYaw,
        float playerPitch,
        int playerId,
        boolean cameraIsPlayer,
        Perspective perspective,
        boolean connectionOpen
    ) {
    }

    private record ScannerBenchmark(int chunks, int visitedBlocks, int availableBlocks, long elapsedNanos) {
    }
}
