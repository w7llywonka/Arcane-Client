package dev.arcaneclient.freecam;

import com.mojang.authlib.GameProfile;
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

/** Owns the client-only player snapshot shown while a detached camera is active. */
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

        OtherClientPlayerEntity snapshot = new VisualBodyEntity(world, player.getGameProfile());
        UUID snapshotUuid = UUID.nameUUIDFromBytes(
            ("arcane-detached-camera-body:" + player.getUuidAsString()).getBytes(StandardCharsets.UTF_8)
        );
        snapshot.setUuid(snapshotUuid);
        int entityId = FIRST_ENTITY_ID;
        while (world.getEntityById(entityId) != null && entityId < -1) entityId++;
        snapshot.setId(entityId);
        copyState(snapshot, player);
        snapshot.setNoGravity(true);
        snapshot.setInvisible(false);
        snapshot.setInvulnerable(true);
        snapshot.setSilent(true);
        snapshot.noClip = true;

        world.addEntity(snapshot);
        body = snapshot;
    }

    static void sync(ClientPlayerEntity player) {
        OtherClientPlayerEntity snapshot = body;
        if (snapshot == null || snapshot.isRemoved()) return;
        copyState(snapshot, player);
    }

    private static void copyState(OtherClientPlayerEntity snapshot, ClientPlayerEntity player) {
        snapshot.copyPositionAndRotation(player);
        snapshot.setHeadYaw(player.getHeadYaw());
        snapshot.setBodyYaw(player.bodyYaw);
        snapshot.setPose(player.getPose());
        snapshot.setOnGround(player.isOnGround());
        snapshot.setSneaking(player.isSneaking());
        snapshot.setSprinting(player.isSprinting());
        snapshot.setSwimming(player.isSwimming());
        snapshot.setHealth(player.getHealth());
        snapshot.setVelocity(Vec3d.ZERO);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snapshot.equipStack(slot, player.getEquippedStack(slot).copy());
        }
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

    static boolean owns(Entity entity) {
        return entity != null && entity == body;
    }

    static Entity entity() {
        return body;
    }

    /** Renders normally but is excluded from crosshair picking, attacks, use, and collision. */
    private static final class VisualBodyEntity extends OtherClientPlayerEntity {
        private VisualBodyEntity(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public boolean canHit() {
            return false;
        }

        @Override
        public boolean isAttackable() {
            return false;
        }

        @Override
        public boolean isInteractable() {
            return false;
        }

        @Override
        public boolean collidesWith(Entity other) {
            return false;
        }

        @Override
        public boolean isCollidable(Entity other) {
            return false;
        }
    }
}
