package dev.arcaneclient.mixin;

import dev.arcaneclient.network.PacketObserverGuard;
import dev.arcaneclient.network.PacketSignalBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "onBlockUpdate", at = @At("HEAD"))
    private void arcaneclient$blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run(
            "growth block update",
            () -> PacketSignalBridge.onBlockUpdate(packet.getPos(), packet.getState())
        );
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
    private void arcaneclient$sectionBlockUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run(
            "growth section update",
            () -> PacketSignalBridge.onSectionBlockUpdate(packet)
        );
    }
}
