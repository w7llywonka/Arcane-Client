package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.freecam.DetachedCameraPose;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.screen.ArcaneSettingsScreen;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@SuppressWarnings("UnstableApiUsage")
public final class ArcaneCameraClientGameTest implements FabricClientGameTest {
    private static final double POSITION_EPSILON = 0.05;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(20);
            prepare(context);
            testFreecam(context);
            testFreelook(context);
            testModeSwitching(context);
            testInterface(context);
        } finally {
            context.runOnClient(client -> {
                FreecamController.disable(client);
                FreelookController.disable(client);
            });
        }
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
        context.runOnClient(FreecamController::enable);
        context.waitTicks(2);

        Snapshot enabled = snapshot(context);
        require(FreecamController.isActive(), "Freecam did not activate");
        require(!enabled.cameraIsPlayer(), "Freecam did not install a detached camera entity");
        require(FreecamController.hasVisualBody(), "Freecam did not create the visible stationary body");
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
            Entity detachedCamera = client.getCameraEntity();
            require(detachedCamera != null && detachedCamera != client.player, "Freecam camera disappeared before interaction test");
            client.interactionManager.attackEntity(client.player, detachedCamera);
        });
        context.waitTicks(3);
        Snapshot afterInvalidAttack = snapshot(context);
        require(afterInvalidAttack.connectionOpen(), "client-only camera attack escaped to the integrated server");
        require(FreecamController.isActive(), "Freecam was lost after guarded camera interaction");

        context.runOnClient(FreecamController::disable);
        context.waitTicks(2);
        Snapshot disabled = snapshot(context);
        require(!FreecamController.isActive(), "Freecam did not disable");
        require(!FreecamController.hasVisualBody(), "Freecam left its visual body in the world after disabling");
        require(disabled.cameraIsPlayer(), "Freecam did not restore the player camera");
        require(disabled.perspective() == Perspective.FIRST_PERSON, "Freecam did not restore the previous perspective");
        assertHudAvailable(context, "Freecam disabled");
        ArcaneClient.LOGGER.info("[QA] Freecam end-to-end test passed");
    }

    private static void testFreelook(ClientGameTestContext context) {
        ArcaneClient.LOGGER.info("[QA] Starting Freelook end-to-end test");
        Snapshot bodyStart = snapshot(context);
        context.runOnClient(FreelookController::enable);
        context.waitTicks(2);

        Snapshot enabled = snapshot(context);
        require(FreelookController.isActive(), "Freelook did not activate");
        require(!enabled.cameraIsPlayer(), "Freelook did not install an orbit camera");
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

        context.getInput().holdKeyFor(key(context, options -> options.forwardKey), 10);
        context.waitTicks(2);
        Snapshot moved = snapshot(context);
        assertAnchored(moved, "Freelook remaining anchored after movement input");
        assertHudAvailable(context, "Freelook anchored");
        context.takeScreenshot("arcane-freelook-orbit-hotbar");

        context.runOnClient(FreelookController::disable);
        context.waitTicks(2);
        Snapshot disabled = snapshot(context);
        require(!FreelookController.isActive(), "Freelook did not disable");
        require(disabled.cameraIsPlayer(), "Freelook did not restore the player camera");
        require(disabled.perspective() == Perspective.FIRST_PERSON, "Freelook did not restore the previous perspective");
        assertHudAvailable(context, "Freelook disabled");
        ArcaneClient.LOGGER.info("[QA] Freelook end-to-end test passed");
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
            Entity camera = client.getCameraEntity();
            Vec3d bodyEye = client.player.getEyePos();
            DetachedCameraPose.advance(
                camera,
                bodyEye.x,
                bodyEye.y + 0.75 - camera.getStandingEyeHeight(),
                bodyEye.z + 4.0
            );
            Vec3d offset = bodyEye.subtract(camera.getEyePos());
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-offset.y, horizontal));
            DetachedCameraPose.rotate(camera, yaw, pitch);
            Perspective perspective = client.options.getPerspective();
            client.gameRenderer.getCamera().update(
                client.world,
                camera,
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
                "[QA] Camera entity yaw {} pitch {}; rendered camera at {} yaw {} pitch {}; body alignment {}",
                client.getCameraEntity().getYaw(),
                client.getCameraEntity().getPitch(),
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
        require(Math.abs(snapshot.cameraPos().x - snapshot.playerPos().x) < POSITION_EPSILON, stage + " drifted on X");
        require(Math.abs(snapshot.cameraPos().z - snapshot.playerPos().z) < POSITION_EPSILON, stage + " drifted on Z");
        require(Math.abs(snapshot.cameraPos().y - snapshot.playerPos().y) < 2.0, stage + " drifted vertically");
    }

    private static void assertHudAvailable(ClientGameTestContext context, String stage) {
        context.runOnClient(client -> {
            require(!client.options.hudHidden, stage + " hid the HUD/hotbar");
            require(client.currentScreen == null, stage + " left a screen covering the hotbar");
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
            boolean open = client.getNetworkHandler() != null && client.getNetworkHandler().getConnection().isOpen();
            return new Snapshot(
                new Vec3d(camera.getX(), camera.getY(), camera.getZ()),
                new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()),
                camera.getYaw(),
                camera.getPitch(),
                client.gameRenderer.getCamera().getYaw(),
                client.gameRenderer.getCamera().getPitch(),
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
}
