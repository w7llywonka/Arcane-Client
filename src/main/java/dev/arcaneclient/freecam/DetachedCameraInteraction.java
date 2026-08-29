package dev.arcaneclient.freecam;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Safe body-origin interactions shared by Freecam and Freelook. */
@Environment(EnvType.CLIENT)
public final class DetachedCameraInteraction {
    private static boolean breaking;

    private DetachedCameraInteraction() {
    }

    public static boolean isActive() {
        return FreecamController.isActive() || FreelookController.isActive();
    }

    static void tickMining(
        MinecraftClient client,
        ClientPlayerEntity player,
        float yaw,
        float pitch,
        boolean enabled
    ) {
        if (!enabled
            || !client.options.attackKey.isPressed()
            || client.interactionManager == null
            || client.world == null) {
            stopMining(client);
            return;
        }

        Vec3d start = player.getEyePos();
        Vec3d end = FreecamMining.rayEnd(start, yaw, pitch, player.getBlockInteractionRange());
        HitResult result = client.world.raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            player
        ));
        if (!(result instanceof BlockHitResult blockHit) || result.getType() != HitResult.Type.BLOCK) {
            stopMining(client);
            return;
        }

        boolean progressed = client.interactionManager.updateBlockBreakingProgress(
            blockHit.getBlockPos(),
            blockHit.getSide()
        );
        breaking = true;
        if (progressed) player.swingHand(Hand.MAIN_HAND);
    }

    public static void stopMining(MinecraftClient client) {
        if (breaking && client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
        breaking = false;
    }
}
