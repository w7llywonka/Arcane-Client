package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.utility.AutoToolController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Safe body-origin interactions shared by Freecam and Freelook. */
@Environment(EnvType.CLIENT)
public final class DetachedCameraInteraction {
    private static boolean breaking;

    private DetachedCameraInteraction() {
    }

    public static boolean isActive() {
        return FreecamController.isActive() || FreelookController.isActive();
    }

    public static HitResult itemUseTarget(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return client.hitResult;
        }
        float yaw = FreecamController.isActive() ? FreecamController.playerYaw() : player.getYRot();
        float pitch = FreecamController.isActive() ? FreecamController.playerPitch() : player.getXRot();
        Vec3 start = player.getEyePosition();
        Vec3 end = FreecamMining.rayEnd(start, yaw, pitch, player.blockInteractionRange());
        if (FreecamController.isActive()) {
            Vec3 direction = end.subtract(start);
            return BlockHitResult.miss(end, Direction.getApproximateNearest(direction), BlockPos.containing(end));
        }
        return client.level.clip(new ClipContext(
            start,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        ));
    }

    static void tickMining(
        Minecraft client,
        LocalPlayer player,
        float yaw,
        float pitch,
        boolean enabled
    ) {
        if (!enabled
            || !client.options.keyAttack.isDown()
            || client.gameMode == null
            || client.level == null) {
            stopMining(client);
            return;
        }

        Vec3 start = player.getEyePosition();
        Vec3 end = FreecamMining.rayEnd(start, yaw, pitch, player.blockInteractionRange());
        HitResult result = client.level.clip(new ClipContext(
            start,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        ));
        if (!(result instanceof BlockHitResult blockHit) || result.getType() != HitResult.Type.BLOCK) {
            stopMining(client);
            return;
        }

        ArcaneConfig config = ArcaneClient.config();
        if (config != null && config.autoTool) {
            AutoToolController.selectForState(
                client,
                player,
                client.level.getBlockState(blockHit.getBlockPos()),
                config.autoToolPreserveDurability
            );
        } else {
            AutoToolController.restore(client);
        }
        boolean progressed = client.gameMode.continueDestroyBlock(
            blockHit.getBlockPos(),
            blockHit.getDirection()
        );
        breaking = true;
        if (progressed) player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
    }

    public static void stopMining(Minecraft client) {
        if (breaking && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
        breaking = false;
        AutoToolController.restore(client);
    }
}
