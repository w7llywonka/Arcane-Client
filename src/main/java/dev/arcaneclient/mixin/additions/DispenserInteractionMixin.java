package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.dispenser.DispenserHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class DispenserInteractionMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void arcane$stopDispenserOnInterference(int syncId, int slot, int button, ContainerInput action,
                                                   Player player, CallbackInfo ci) {
        if (DispenserHelper.interruptClick(Minecraft.getInstance(), syncId)) ci.cancel();
    }
}
