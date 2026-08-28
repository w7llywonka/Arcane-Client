package dev.arcaneclient.freecam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import org.junit.jupiter.api.Test;

final class FreecamPacketGuardTest {
    @Test
    void blocksEveryCameraOriginInteractionPacket() {
        assertTrue(FreecamPacketGuard.isUnsafeInteractionType(PlayerInteractEntityC2SPacket.class));
        assertTrue(FreecamPacketGuard.isUnsafeInteractionType(PlayerInteractBlockC2SPacket.class));
        assertTrue(FreecamPacketGuard.isUnsafeInteractionType(PlayerInteractItemC2SPacket.class));
    }

    @Test
    void preservesPlayerActionPacketsUsedByBodyOriginMining() {
        assertFalse(FreecamPacketGuard.isUnsafeInteractionType(PlayerActionC2SPacket.class));
    }
}
