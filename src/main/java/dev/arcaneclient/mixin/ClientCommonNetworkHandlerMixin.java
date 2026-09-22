package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamPacketGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fail-closed outbound interaction boundary for Freecam. */
@Environment(EnvType.CLIENT)
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$blockUnsafeFreecamInteraction(Packet<?> packet, CallbackInfo ci) {
        if (!FreecamPacketGuard.shouldBlock(packet)) return;
        FreecamPacketGuard.logBlocked(packet);
        ci.cancel();
    }
}
