package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.esp.BlockEntityEspClassifier;
import dev.arcaneclient.esp.EspRanges;
import dev.arcaneclient.esp.StorageDiscoveryTracker;
import dev.arcaneclient.performance.PerformanceProfile;
import dev.arcaneclient.render.TracerLines;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(value=EnvType.CLIENT)
public final class EspRenderer {
    private static final VoxelShape BLOCK_BOX = Shapes.box((double)0.03, (double)0.03, (double)0.03, (double)0.97, (double)0.97, (double)0.97);
    private static List<Target> targets = List.of();
    private static long lastRefresh = Long.MIN_VALUE;
    private static final StorageDiscoveryTracker DISCOVERIES = new StorageDiscoveryTracker(65_536);
    private static ClientLevel discoveryWorld;

    private EspRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(EspRenderer::render);
    }

    public static void tick(Minecraft client) {
        if (ArcaneSettingsScreen.isOpen(client)) return;
        ArcaneConfig config = ArcaneClient.config();
        if (client.level != discoveryWorld) {
            discoveryWorld = client.level;
            DISCOVERIES.reset();
            lastRefresh = Long.MIN_VALUE;
        }
        if ((!config.esp && !config.blockEntityDebug) || client.level == null || client.getCameraEntity() == null) {
            targets = List.of();
            return;
        }
        PerformanceProfile profile = config.performanceProfile();
        long gameTime = client.level.getGameTime();
        if (lastRefresh != Long.MIN_VALUE && gameTime >= lastRefresh && gameTime - lastRefresh < profile.snapshotRefreshTicks()) {
            return;
        }
        lastRefresh = gameTime;
        Entity cameraEntity = client.getCameraEntity();
        int centerX = SectionPos.blockToSectionCoord((int)cameraEntity.getBlockX());
        int centerZ = SectionPos.blockToSectionCoord((int)cameraEntity.getBlockZ());
        int radius = profile.storageRadiusChunks();
        int targetLimit = profile.storageTargetLimit();
        PriorityQueue<RankedTarget> nearestStorage = new PriorityQueue<>(
            Comparator.comparingDouble(RankedTarget::horizontalDistanceSquared)
                .thenComparingInt(ranked -> ranked.target.pos.getY())
                .reversed()
        );
        PriorityQueue<RankedTarget> nearestDebug = new PriorityQueue<>(
            Comparator.comparingDouble(RankedTarget::horizontalDistanceSquared)
                .thenComparingInt(ranked -> ranked.target.pos.getY())
                .reversed()
        );
        ArrayList<StorageDiscovery> discovered = new ArrayList<>();
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                LevelChunk chunk = client.level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
                    String path = id == null ? "unknown" : id.getPath();
                    BlockPos pos = blockEntity.getBlockPos().immutable();
                    int storageColor = config.esp ? BlockEntityEspClassifier.color(path) : 0;
                    int debugColor = config.blockEntityDebug ? BlockEntityEspClassifier.debugColor(pos.getY()) : 0;
                    boolean storageTarget = storageColor != 0;
                    boolean debugTarget = debugColor != 0;
                    if (!storageTarget && !debugTarget) continue;
                    if (storageTarget && config.storageChatAlerts && BlockEntityEspClassifier.isContainerTarget(path) && DISCOVERIES.markNew(pos.asLong())) {
                        discovered.add(new StorageDiscovery(pos, path));
                    }
                    double horizontalDistanceSquared = EspRanges.horizontalDistanceSquared(
                        pos.getX() + 0.5,
                        pos.getZ() + 0.5,
                        cameraEntity.getX(),
                        cameraEntity.getZ()
                    );
                    if (storageTarget) {
                        offerNearest(nearestStorage, new Target(pos, storageColor, true, debugTarget), horizontalDistanceSquared, targetLimit);
                    }
                    if (debugTarget && !storageTarget) {
                        offerNearest(nearestDebug, new Target(pos, debugColor, false, true), horizontalDistanceSquared, targetLimit);
                    }
                }
            }
        }
        ArrayList<RankedTarget> ranked = new ArrayList<>(nearestStorage.size() + nearestDebug.size());
        ranked.addAll(nearestStorage);
        ranked.addAll(nearestDebug);
        ranked.sort(
            Comparator.comparingDouble(RankedTarget::horizontalDistanceSquared)
                .thenComparingInt(candidate -> candidate.target.pos.getY())
        );
        targets = ranked.stream().map(RankedTarget::target).toList();
        discovered.sort(Comparator.comparingDouble(discovery -> EspRanges.horizontalDistanceSquared(
            discovery.pos.getX() + 0.5,
            discovery.pos.getZ() + 0.5,
            cameraEntity.getX(),
            cameraEntity.getZ()
        )));
        publishDiscoveries(client, discovered);
    }

    private static void offerNearest(PriorityQueue<RankedTarget> queue, Target target, double distanceSquared, int targetLimit) {
        queue.add(new RankedTarget(target, distanceSquared));
        if (queue.size() > targetLimit) {
            queue.poll();
        }
    }

    private static void publishDiscoveries(Minecraft client, List<StorageDiscovery> discovered) {
        if (client.player == null || discovered.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (StorageDiscovery discovery : discovered) {
            counts.merge(displayName(discovery.type), 1, Integer::sum);
        }
        String types = counts.entrySet().stream()
            .limit(4)
            .map(entry -> entry.getValue() == 1 ? entry.getKey() : entry.getKey() + " x" + entry.getValue())
            .reduce((left, right) -> left + ", " + right)
            .orElse("container");
        if (counts.size() > 4) {
            types += ", +" + (counts.size() - 4) + " types";
        }
        BlockPos nearest = discovered.getFirst().pos;
        String message = discovered.size() == 1
            ? "Storage found: " + types + " at " + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ()
            : "Storage found: " + discovered.size() + " block entities (" + types + "), nearest at "
                + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ();
        client.player.sendSystemMessage(
            Component.literal("[Arcane] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(message).withStyle(ChatFormatting.GREEN))
        );
    }

    private static String displayName(String path) {
        return path.replace('_', ' ');
    }

    private static void render(LevelRenderContext context) {
        renderedDebugTracerCount = 0;
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client) || (!config.esp && !config.blockEntityDebug) || client.level == null || targets.isEmpty()) {
            return;
        }
        Entity cameraEntity = client.getCameraEntity();
        if (context.poseStack() == null || cameraEntity == null) {
            return;
        }
        Vec3 camera = Render263.camera(context);
        PerformanceProfile profile = config.performanceProfile();
        Render263.Batch batch = Render263.batch(context);
        for (Target target : targets) {
            BlockPos pos = target.pos();
            batch.outline(BLOCK_BOX, pos.getX(), pos.getY(), pos.getZ(), target.color(), 2.0f, true);
            if (!((target.storageTarget() && config.esp && config.storageTracers)
                || (target.debugTarget() && config.blockEntityDebug && config.blockEntityDebugTracers))) continue;
            batch.line(camera.x, camera.y, camera.z,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, target.color(), 1.25f, true);
            if (target.debugTarget() && config.blockEntityDebug && config.blockEntityDebugTracers) renderedDebugTracerCount++;
        }
        if (profile.filledStorageBoxes()) {
            for (Target target : targets) {
                BlockPos pos = target.pos();
                batch.filledBox(pos.getX() + 0.05, pos.getY() + 0.05, pos.getZ() + 0.05,
                    pos.getX() + 0.95, pos.getY() + 0.95, pos.getZ() + 0.95,
                    0x30000000 | target.color() & 0xFFFFFF, true);
            }
        }
        batch.submit();
    }

    public static int targetCount() {
        return (int)targets.stream().filter(Target::storageTarget).count();
    }

    public static int debugTargetCount() {
        return (int)targets.stream().filter(Target::debugTarget).count();
    }

    private static int renderedDebugTracerCount;
    public static int renderedDebugTracerCount() { return renderedDebugTracerCount; }

    @Environment(value=EnvType.CLIENT)
    private record Target(BlockPos pos, int color, boolean storageTarget, boolean debugTarget) {
    }

    @Environment(value=EnvType.CLIENT)
    private record RankedTarget(Target target, double horizontalDistanceSquared) {
    }

    @Environment(value=EnvType.CLIENT)
    private record StorageDiscovery(BlockPos pos, String type) {
    }
}
