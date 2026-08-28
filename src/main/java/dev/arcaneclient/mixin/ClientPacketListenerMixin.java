package dev.arcaneclient.mixin;

import dev.arcaneclient.network.PacketObserverGuard;
import dev.arcaneclient.network.PacketSignalBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={ClientPlayNetworkHandler.class})
public abstract class ClientPacketListenerMixin {
    @Inject(method={"onChunkData"}, at={@At(value="HEAD")})
    private void arcaneclient$chunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("chunk data", () -> PacketSignalBridge.onChunkData(packet));
    }

    @Inject(method={"onBlockUpdate"}, at={@At(value="HEAD")})
    private void arcaneclient$blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block update", () -> PacketSignalBridge.onBlockUpdate(packet.getPos(), packet.getState()));
    }

    @Inject(method={"onChunkDeltaUpdate"}, at={@At(value="HEAD")})
    private void arcaneclient$sectionBlockUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("section block update", () -> PacketSignalBridge.onSectionBlockUpdate(packet));
    }

    @Inject(method={"onBlockEntityUpdate"}, at={@At(value="HEAD")})
    private void arcaneclient$blockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block entity", () -> PacketSignalBridge.onBlockEntity(packet.getPos(), packet.getBlockEntityType(), "block entity update"));
    }

    @Inject(method={"onBlockEvent"}, at={@At(value="HEAD")})
    private void arcaneclient$blockEvent(BlockEventS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block event", () -> PacketSignalBridge.onBlockEvent(packet));
    }

    @Inject(method={"onBlockBreakingProgress"}, at={@At(value="HEAD")})
    private void arcaneclient$blockBreaking(BlockBreakingProgressS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("block breaking", () -> PacketSignalBridge.onBlockBreaking(packet));
    }

    @Inject(method={"onPlaySound"}, at={@At(value="HEAD")})
    private void arcaneclient$sound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("sound", () -> PacketSignalBridge.onSound(packet));
    }

    @Inject(method={"onParticle"}, at={@At(value="HEAD")})
    private void arcaneclient$particle(ParticleS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("particle", () -> PacketSignalBridge.onParticle(packet));
    }

    @Inject(method={"onWorldEvent"}, at={@At(value="HEAD")})
    private void arcaneclient$levelEvent(WorldEventS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("level event", () -> PacketSignalBridge.onLevelEvent(packet));
    }

    @Inject(method={"onLightUpdate"}, at={@At(value="HEAD")})
    private void arcaneclient$lightUpdate(LightUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("light update", () -> PacketSignalBridge.onLightUpdate(packet));
    }

    @Inject(method={"onEntitySpawn"}, at={@At(value="TAIL")})
    private void arcaneclient$entitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity spawn", () -> PacketSignalBridge.onEntitySpawn(packet));
    }

    @Inject(method={"onEntityTrackerUpdate"}, at={@At(value="TAIL")})
    private void arcaneclient$entityData(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity metadata", () -> PacketSignalBridge.onEntityChanged(packet.id(), "entity metadata"));
    }

    @Inject(method={"onEntityEquipmentUpdate"}, at={@At(value="TAIL")})
    private void arcaneclient$equipment(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity equipment", () -> PacketSignalBridge.onEntityChanged(packet.getEntityId(), "entity equipment"));
    }

    @Inject(method={"onEntityAttach"}, at={@At(value="TAIL")})
    private void arcaneclient$leash(EntityAttachS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity link", () -> PacketSignalBridge.onEntityLink(packet));
    }

    @Inject(method={"onEntityPassengersSet"}, at={@At(value="TAIL")})
    private void arcaneclient$passengers(EntityPassengersSetS2CPacket packet, CallbackInfo ci) {
        PacketObserverGuard.run("entity passengers", () -> PacketSignalBridge.onEntityChanged(packet.getEntityId(), "entity passengers"));
    }
}
