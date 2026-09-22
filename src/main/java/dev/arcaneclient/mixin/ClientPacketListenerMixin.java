package dev.arcaneclient.mixin;

import dev.arcaneclient.network.PacketObserverGuard;
import dev.arcaneclient.network.PacketSignalBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ClientPacketListener.class})
public abstract class ClientPacketListenerMixin {
    @Inject(method={"handleLevelChunkWithLight"}, at={@At(value="HEAD")})
    private void arcaneclient$chunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("chunk data", () -> PacketSignalBridge.onChunkData(packet));
    }

    @Inject(method={"handleBlockUpdate"}, at={@At(value="HEAD")})
    private void arcaneclient$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block update", () -> PacketSignalBridge.onBlockUpdate(packet.getPos(), packet.getBlockState()));
    }

    @Inject(method={"handleChunkBlocksUpdate"}, at={@At(value="HEAD")})
    private void arcaneclient$sectionBlockUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("section block update", () -> PacketSignalBridge.onSectionBlockUpdate(packet));
    }

    @Inject(method={"handleBlockEntityData"}, at={@At(value="HEAD")})
    private void arcaneclient$blockEntityUpdate(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block entity", () -> PacketSignalBridge.onBlockEntity(packet.getPos(), packet.getType(), "block entity update"));
    }

    @Inject(method={"handleBlockEvent"}, at={@At(value="HEAD")})
    private void arcaneclient$blockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block event", () -> PacketSignalBridge.onBlockEvent(packet));
    }

    @Inject(method={"handleBlockDestruction"}, at={@At(value="HEAD")})
    private void arcaneclient$blockBreaking(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block breaking", () -> PacketSignalBridge.onBlockBreaking(packet));
    }

    @Inject(method={"handleSoundEvent"}, at={@At(value="HEAD")})
    private void arcaneclient$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("sound", () -> PacketSignalBridge.onSound(packet));
    }

    @Inject(method={"handleParticleEvent"}, at={@At(value="HEAD")})
    private void arcaneclient$particle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("particle", () -> PacketSignalBridge.onParticle(packet));
    }

    @Inject(method={"handleLevelEvent"}, at={@At(value="HEAD")})
    private void arcaneclient$levelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("level event", () -> PacketSignalBridge.onLevelEvent(packet));
    }

    @Inject(method={"handleLightUpdatePacket"}, at={@At(value="HEAD")})
    private void arcaneclient$lightUpdate(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("light update", () -> PacketSignalBridge.onLightUpdate(packet));
    }

    @Inject(method={"handleAddEntity"}, at={@At(value="TAIL")})
    private void arcaneclient$entitySpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity spawn", () -> PacketSignalBridge.onEntitySpawn(packet));
    }

    @Inject(method={"handleSetEntityData"}, at={@At(value="TAIL")})
    private void arcaneclient$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity metadata", () -> PacketSignalBridge.onEntityChanged(packet.id(), "entity metadata"));
    }

    @Inject(method={"handleSetEquipment"}, at={@At(value="TAIL")})
    private void arcaneclient$equipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity equipment", () -> PacketSignalBridge.onEntityChanged(packet.getEntity(), "entity equipment"));
    }

    @Inject(method={"handleEntityLinkPacket"}, at={@At(value="TAIL")})
    private void arcaneclient$leash(ClientboundSetEntityLinkPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity link", () -> PacketSignalBridge.onEntityLink(packet));
    }

    @Inject(method={"handleSetEntityPassengersPacket"}, at={@At(value="TAIL")})
    private void arcaneclient$passengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity passengers", () -> PacketSignalBridge.onEntityChanged(packet.getVehicle(), "entity passengers"));
    }
}
