package dev.arcaneclient.freecam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.junit.jupiter.api.Test;

final class FreecamPacketGuardTest {
    @Test
    void blocksCameraOriginWorldTargetsButAllowsHeldItemUse() {
        assertTrue(FreecamPacketGuard.isUnsafeInteractionType(ServerboundInteractPacket.class));
        assertTrue(FreecamPacketGuard.isUnsafeInteractionType(ServerboundUseItemOnPacket.class));
        assertFalse(FreecamPacketGuard.isUnsafeInteractionType(ServerboundUseItemPacket.class));
    }

    @Test
    void preservesPlayerActionPacketsUsedByBodyOriginMining() {
        assertFalse(FreecamPacketGuard.isUnsafeInteractionType(ServerboundPlayerActionPacket.class));
    }
}
