package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.susfinder.SusChunkFinderController;
import dev.arcaneclient.network.PacketObserverGuard;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This vanilla method handles both the initial chunk packet and standalone block-light updates. */
@Mixin(ClientPacketListener.class)
public abstract class SusFinderNetworkMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "applyLightData", at = @At("TAIL"))
    private void arcaneclient$susFinderLight(int x, int z, ClientboundLightUpdatePacketData data, boolean nonEdge, CallbackInfo ci) {
        PacketObserverGuard.run("sus finder server block light", () -> SusChunkFinderController.receive(level, x, z, data));
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void arcaneclient$susFinderBlock(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("sus finder block invalidation", () ->
            SusChunkFinderController.blocksChanged(level, packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4));
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void arcaneclient$susFinderSection(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("sus finder section invalidation", () -> {
            // All updates in this packet share a section; invalidate its chunk once.
            boolean[] visited = {false};
            packet.runUpdates((pos, state) -> {
                if (!visited[0]) {
                    visited[0] = true;
                    SusChunkFinderController.blocksChanged(level, pos.getX() >> 4, pos.getZ() >> 4);
                }
            });
        });
    }
}
