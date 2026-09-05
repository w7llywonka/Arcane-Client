package dev.arcaneclient.mixin;

import dev.arcaneclient.utility.QualityOfLifeController;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops durability-consuming interaction packets before a protected item can break. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardInitialBlockAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLifeController.shouldProtect(playerMainHand())) cir.setReturnValue(false);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardBlockProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLifeController.shouldProtect(playerMainHand())) cir.setReturnValue(false);
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardEntityAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (QualityOfLifeController.shouldProtect(player.getMainHandStack())) ci.cancel();
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardItemUse(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (QualityOfLifeController.shouldProtect(player.getStackInHand(hand))) {
            // PASS lets vanilla try the other hand without sending an unsafe packet for this one.
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    private static net.minecraft.item.ItemStack playerMainHand() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client.player == null ? net.minecraft.item.ItemStack.EMPTY : client.player.getMainHandStack();
    }
}
