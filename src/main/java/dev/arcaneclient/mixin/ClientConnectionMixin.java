package dev.arcaneclient.mixin;

import dev.arcaneclient.freecam.FreecamPacketGuard;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final outbound boundary: no camera-origin interaction packet may reach the channel. */
@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(
        method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void arcaneclient$blockUnsafeFreecamInteraction(
        Packet<?> packet,
        ChannelFutureListener callbacks,
        boolean flush,
        CallbackInfo ci
    ) {
        if (!FreecamPacketGuard.shouldBlock(packet)) return;
        FreecamPacketGuard.logBlocked(packet);
        ci.cancel();
    }
}
