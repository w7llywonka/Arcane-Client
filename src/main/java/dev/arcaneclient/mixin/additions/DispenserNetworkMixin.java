package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.dispenser.DispenserHelper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TAIL runs after vanilla has applied each packet on the client thread. */
@Mixin(ClientPacketListener.class)
public abstract class DispenserNetworkMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void arcane$confirmDispenserSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        DispenserHelper.onSlotUpdate(packet);
    }

    @Inject(method = "handleSetCursorItem", at = @At("TAIL"))
    private void arcane$confirmDispenserCursor(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        DispenserHelper.onCursorUpdate(packet.contents());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void arcane$confirmDispenserInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        DispenserHelper.onInventory(packet);
    }
}
