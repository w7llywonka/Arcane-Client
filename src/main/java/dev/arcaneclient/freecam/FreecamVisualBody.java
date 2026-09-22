package dev.arcaneclient.freecam;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

/** Owns the client-only player snapshot shown while a detached camera is active. */
@Environment(EnvType.CLIENT)
final class FreecamVisualBody {
    private static final int FIRST_ENTITY_ID = -2_034_119_937;
    private static RemotePlayer body;

    private FreecamVisualBody() {
    }

    static void spawn(Minecraft client, LocalPlayer player) {
        remove();
        ClientLevel world = client.level;
        if (world == null) return;

        RemotePlayer snapshot = new VisualBodyEntity(
            world,
            player.getGameProfile(),
            player.getSkin()
        );
        UUID snapshotUuid = UUID.nameUUIDFromBytes(
            ("arcane-detached-camera-body:" + player.getStringUUID()).getBytes(StandardCharsets.UTF_8)
        );
        snapshot.setUUID(snapshotUuid);
        int entityId = FIRST_ENTITY_ID;
        while (world.getEntity(entityId) != null && entityId < -1) entityId++;
        snapshot.setId(entityId);
        copyState(snapshot, player);
        snapshot.setNoGravity(true);
        snapshot.setInvisible(false);
        snapshot.setSilent(true);
        snapshot.noPhysics = true;

        world.addEntity(snapshot);
        body = snapshot;
    }

    static void sync(LocalPlayer player) {
        RemotePlayer snapshot = body;
        if (snapshot == null || snapshot.isRemoved()) return;
        copyState(snapshot, player);
    }

    private static void copyState(RemotePlayer snapshot, LocalPlayer player) {
        snapshot.copyPosition(player);
        snapshot.setYHeadRot(player.getYHeadRot());
        snapshot.setYBodyRot(player.yBodyRot);
        snapshot.setPose(player.getPose());
        snapshot.setOnGround(player.onGround());
        snapshot.setShiftKeyDown(player.isShiftKeyDown());
        snapshot.setSprinting(player.isSprinting());
        snapshot.setSwimming(player.isSwimming());
        snapshot.setHealth(player.getHealth());
        snapshot.setDeltaMovement(Vec3.ZERO);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snapshot.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
    }

    static void remove() {
        RemotePlayer snapshot = body;
        body = null;
        if (snapshot == null) return;
        if (snapshot.level() instanceof ClientLevel world
            && world.getEntity(snapshot.getId()) == snapshot) {
            world.removeEntity(snapshot.getId(), Entity.RemovalReason.DISCARDED);
        } else if (!snapshot.isRemoved()) {
            snapshot.discard();
        }
    }

    static boolean isPresent() {
        RemotePlayer snapshot = body;
        return snapshot != null
            && !snapshot.isRemoved()
            && snapshot.level() instanceof ClientLevel world
            && world.getEntity(snapshot.getId()) == snapshot;
    }

    static boolean owns(Entity entity) {
        return entity != null && entity == body;
    }

    static Entity entity() {
        return body;
    }

    /** Renders normally but is excluded from crosshair picking, attacks, use, and collision. */
    private static final class VisualBodyEntity extends RemotePlayer {
        private final PlayerSkin skin;

        private VisualBodyEntity(ClientLevel world, GameProfile profile, PlayerSkin skin) {
            super(world, profile);
            this.skin = skin;
        }

        @Override
        public PlayerSkin getSkin() {
            return skin;
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        public boolean isAttackable() {
            return false;
        }

        @Override
        public boolean canInteractWithLevel() {
            return false;
        }

        @Override
        public boolean canCollideWith(Entity other) {
            return false;
        }

        @Override
        public boolean canBeCollidedWith(Entity other) {
            return false;
        }
    }
}
