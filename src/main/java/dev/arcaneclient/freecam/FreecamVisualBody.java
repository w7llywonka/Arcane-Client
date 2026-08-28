package dev.arcaneclient.freecam;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.Vec3d;

/** Owns the client-only player snapshot shown while the real player remains server-controlled. */
@Environment(EnvType.CLIENT)
final class FreecamVisualBody {
    private static final int FIRST_ENTITY_ID = -2_034_119_937;
    private static OtherClientPlayerEntity body;

    private FreecamVisualBody() {
    }

    static void spawn(MinecraftClient client, ClientPlayerEntity player) {
        remove();
        ClientWorld world = client.world;
        if (world == null) return;

        OtherClientPlayerEntity snapshot = new OtherClientPlayerEntity(world, player.getGameProfile());
        UUID snapshotUuid = UUID.nameUUIDFromBytes(
            ("arcane-freecam-body:" + player.getUuidAsString()).getBytes(StandardCharsets.UTF_8)
        );
        snapshot.setUuid(snapshotUuid);
        int entityId = FIRST_ENTITY_ID;
        while (world.getEntityById(entityId) != null && entityId < -1) entityId++;
        snapshot.setId(entityId);
        snapshot.copyPositionAndRotation(player);
        snapshot.setHeadYaw(player.getHeadYaw());
        snapshot.setBodyYaw(player.bodyYaw);
        snapshot.setPose(player.getPose());
        snapshot.setOnGround(player.isOnGround());
        snapshot.setSneaking(player.isSneaking());
        snapshot.setSprinting(player.isSprinting());
        snapshot.setSwimming(player.isSwimming());
        snapshot.setHealth(player.getHealth());
        snapshot.setNoGravity(true);
        snapshot.noClip = true;
        snapshot.setVelocity(Vec3d.ZERO);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snapshot.equipStack(slot, player.getEquippedStack(slot).copy());
        }

        world.addEntity(snapshot);
        body = snapshot;
    }

    static void remove() {
        OtherClientPlayerEntity snapshot = body;
        body = null;
        if (snapshot == null) return;
        if (snapshot.getEntityWorld() instanceof ClientWorld world
            && world.getEntityById(snapshot.getId()) == snapshot) {
            world.removeEntity(snapshot.getId(), Entity.RemovalReason.DISCARDED);
        } else if (!snapshot.isRemoved()) {
            snapshot.discard();
        }
    }

    static boolean isPresent() {
        OtherClientPlayerEntity snapshot = body;
        return snapshot != null
            && !snapshot.isRemoved()
            && snapshot.getEntityWorld() instanceof ClientWorld world
            && world.getEntityById(snapshot.getId()) == snapshot;
    }
}
