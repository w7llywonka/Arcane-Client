package dev.arcaneclient.network;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.scan.ActivityClassifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class PacketSignalBridge {
    private static final ActivityClassifier CLASSIFIER = new ActivityClassifier();

    private PacketSignalBridge() {
    }

    public static void onBlockUpdate(BlockPos pos, BlockState incoming) {
        MinecraftClient client = clientOnMainThread();
        if (client == null || client.world == null) {
            return;
        }

        BlockState old = client.world.getBlockState(pos);
        if (old.equals(incoming)) {
            return;
        }

        ActivityClassifier.GrowthObservation before = CLASSIFIER.observe(old);
        ActivityClassifier.GrowthObservation after = CLASSIFIER.observe(incoming);
        if (!before.isGrowth() && !after.isGrowth()) {
            return;
        }

        if (before.isGrowth() && after.isGrowth() && before.path().equals(after.path())) {
            if (before.hasStages() && after.hasStages()) {
                int delta = after.stage() - before.stage();
                if (delta > 0) {
                    ArcaneClient.engine().recordLive(
                        pos,
                        SignalCategory.NATURAL_GROWTH,
                        Math.min(80, 12 + delta * 10),
                        "observed growth advance: " + readable(after.path()),
                        2_400,
                        2
                    );
                } else if (delta < 0 && before.kind() == ActivityClassifier.GrowthKind.FIELD) {
                    ArcaneClient.engine().recordLive(
                        pos,
                        SignalCategory.CULTIVATION,
                        Math.min(50, 6 + -delta * 2),
                        "observed crop reset: " + readable(before.path()),
                        3_600,
                        2
                    );
                } else if (delta < 0) {
                    ArcaneClient.engine().recordLive(
                        pos,
                        SignalCategory.NATURAL_GROWTH,
                        8,
                        "observed growth cycle: " + readable(before.path()),
                        2_400,
                        2
                    );
                }
            }
            return;
        }

        if (before.isGrowth() && !after.isGrowth()) {
            boolean fieldHarvest = before.kind() == ActivityClassifier.GrowthKind.FIELD && before.mature();
            int strength = fieldHarvest ? 20 : 8;
            String reason = fieldHarvest ? "mature crop harvested: " : "growth block removed: ";
            ArcaneClient.engine().recordLive(
                pos,
                SignalCategory.CULTIVATION,
                strength,
                reason + readable(before.path()),
                3_600,
                2
            );
            return;
        }

        if (!before.isGrowth() && after.isGrowth()) {
            SignalCategory category = after.kind() == ActivityClassifier.GrowthKind.VERTICAL
                ? SignalCategory.NATURAL_GROWTH
                : SignalCategory.CULTIVATION;
            int strength = category == SignalCategory.CULTIVATION ? 7 : 5;
            ArcaneClient.engine().recordLive(
                pos,
                category,
                strength,
                "new growth observed: " + readable(after.path()),
                2_400,
                2
            );
        }
    }

    public static void onSectionBlockUpdate(ChunkDeltaUpdateS2CPacket packet) {
        if (clientOnMainThread() == null) {
            return;
        }
        packet.visitUpdates((pos, state) -> onBlockUpdate(pos.toImmutable(), state));
    }

    private static MinecraftClient clientOnMainThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.isOnThread() ? client : null;
    }

    private static String readable(String path) {
        return path.replace('_', ' ');
    }
}
