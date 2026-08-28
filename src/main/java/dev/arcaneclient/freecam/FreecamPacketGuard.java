package dev.arcaneclient.freecam;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

/** Last-line protection against camera-origin interactions escaping to a multiplayer server. */
@Environment(EnvType.CLIENT)
public final class FreecamPacketGuard {
    private static long nextLogNanos;

    private FreecamPacketGuard() {
    }

    public static boolean shouldBlock(Packet<?> packet) {
        return FreecamController.isActive() && isUnsafeInteractionType(packet.getClass());
    }

    static boolean isUnsafeInteractionType(Class<?> packetType) {
        return PlayerInteractEntityC2SPacket.class.isAssignableFrom(packetType)
            || PlayerInteractBlockC2SPacket.class.isAssignableFrom(packetType)
            || PlayerInteractItemC2SPacket.class.isAssignableFrom(packetType);
    }

    public static void logBlocked(Packet<?> packet) {
        long now = System.nanoTime();
        if (now < nextLogNanos) return;
        nextLogNanos = now + 1_000_000_000L;
        ArcaneClient.LOGGER.warn("Blocked unsafe {} while Freecam was active", packet.getClass().getSimpleName());
    }
}
