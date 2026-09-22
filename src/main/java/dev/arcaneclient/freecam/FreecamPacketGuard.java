package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

/** Last-line protection against camera-origin block/entity targets escaping to a server. */
@Environment(EnvType.CLIENT)
public final class FreecamPacketGuard {
    private static long nextLogNanos;

    private FreecamPacketGuard() {
    }

    public static boolean shouldBlock(Packet<?> packet) {
        return FreecamController.isActive() && isUnsafeInteractionType(packet.getClass());
    }

    static boolean isUnsafeInteractionType(Class<?> packetType) {
        return ServerboundInteractPacket.class.isAssignableFrom(packetType)
            || ServerboundUseItemOnPacket.class.isAssignableFrom(packetType);
    }

    public static void logBlocked(Packet<?> packet) {
        long now = System.nanoTime();
        if (now < nextLogNanos) return;
        nextLogNanos = now + 1_000_000_000L;
        ArcaneClient.LOGGER.warn("Blocked unsafe {} while Freecam was active", packet.getClass().getSimpleName());
    }
}
