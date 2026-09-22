package dev.arcaneclient.mixin;

import dev.arcaneclient.utility.QualityOfLifeController;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops durability-consuming interaction packets before a protected item can break. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardInitialBlockAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLifeController.shouldProtect(playerMainHand())) cir.setReturnValue(false);
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardBlockProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLifeController.shouldProtect(playerMainHand())) cir.setReturnValue(false);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardEntityAttack(Player player, Entity target, CallbackInfo ci) {
        if (QualityOfLifeController.shouldProtect(player.getMainHandItem())) ci.cancel();
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$guardItemUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (QualityOfLifeController.shouldProtect(player.getItemInHand(hand))) {
            // PASS lets vanilla try the other hand without sending an unsafe packet for this one.
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    private static net.minecraft.world.item.ItemStack playerMainHand() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        return client.player == null ? net.minecraft.world.item.ItemStack.EMPTY : client.player.getMainHandItem();
    }
}
