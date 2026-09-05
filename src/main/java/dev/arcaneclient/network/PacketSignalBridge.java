package dev.arcaneclient.network;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.ScanResult;
import dev.arcaneclient.model.SignalCategory;
import dev.arcaneclient.scan.ActivityClassifier;
import dev.arcaneclient.scan.EvidenceHeuristics;
import dev.arcaneclient.scan.GrowthTransitions;
import dev.arcaneclient.scan.ScannerStorageFilter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3i;

@Environment(value=EnvType.CLIENT)
public final class PacketSignalBridge {
    private static final Set<String> INTERACTION_SOUNDS = Set.of("block.piston.extend", "block.piston.contract", "block.lever.click", "block.stone_button.click_on", "block.stone_button.click_off", "block.wooden_button.click_on", "block.wooden_button.click_off", "block.note_block.harp", "block.respawn_anchor.charge", "block.beacon.activate", "block.anvil.use", "block.grindstone.use", "block.smithing_table.use", "block.copper_bulb.turn_on", "block.copper_bulb.turn_off");
    private static final Set<String> INTERACTION_PROPERTIES = Set.of("powered", "open", "lit", "charges", "bites", "note", "delay", "mode", "disarmed", "occupied");

    private PacketSignalBridge() {
    }

    public static void onChunkData(ChunkDataS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.world == null) {
            return;
        }
        ScanResult.Builder result = ScanResult.builder();
        packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ()).accept((pos, type, tag) -> {
            String id = PacketSignalBridge.path(Registries.BLOCK_ENTITY_TYPE.getId(type));
            if (ScannerStorageFilter.isStoragePath(id)) return;
            int strength = PacketSignalBridge.blockEntityStrength(id);
            if (strength > 0) {
                result.addStatic(SignalCategory.BLOCK_ENTITY, PacketSignalBridge.at(pos), "raw packet " + id, strength);
            }
        });
        if (ArcaneClient.engine().recordsConcealedLight()) {
            int litBytes = 0;
            Iterator iterator = packet.getLightData().getBlockNibbles().iterator();
            while (iterator.hasNext()) {
                byte[] data;
                for (byte value : data = (byte[])iterator.next()) {
                    if (value == 0) continue;
                    ++litBytes;
                }
            }
            if (litBytes > 16) {
                BlockPosition center = new BlockPosition(packet.getChunkX() * 16 + 8, client.world.getBottomY() + 32, packet.getChunkZ() * 16 + 8);
                result.addStatic(SignalCategory.LIGHT_LEAK, center, "separate block-light payload", Math.min(12, 2 + litBytes / 128));
            }
        }
        ArcaneClient.engine().mergeStatic(packet.getChunkX(), packet.getChunkZ(), result.build());
    }

    public static void onBlockUpdate(BlockPos pos, BlockState incoming) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.world == null) {
            return;
        }
        BlockState old = client.world.getBlockState(pos);
        if (old.equals((Object)incoming)) {
            return;
        }
        String oldId = PacketSignalBridge.blockId(old);
        String newId = PacketSignalBridge.blockId(incoming);
        if (ScannerStorageFilter.isStoragePath(oldId) || ScannerStorageFilter.isStoragePath(newId)) {
            return;
        }
        ArcaneClient.engine().recordAmethystState(pos, oldId, newId);
        ArcaneClient.engine().recordCobbledDeepslateState(pos, oldId, newId);
        String location = PacketSignalBridge.hiddenSuffix(client, pos);
        if (PacketSignalBridge.isPlayerFingerprint(newId, incoming)) {
            ArcaneClient.engine().recordLive(pos, SignalCategory.PLACED_BLOCK, 110, "placed fingerprint: " + newId + location);
        }
        GrowthTransitions.GrowthEvent growth = GrowthTransitions.analyze(old, incoming);
        if (growth != null) {
            ArcaneClient.engine().recordLive(pos, growth.category(), growth.strength(), growth.reason() + location, 12000, 3);
        }
        boolean automationChanged = false;
        for (String property : INTERACTION_PROPERTIES) {
            Object before = PacketSignalBridge.property(old, property);
            Object after = PacketSignalBridge.property(incoming, property);
            if (before == null || after == null || before.equals(after)) continue;
            automationChanged = true;
            ArcaneClient.engine().recordLive(pos, SignalCategory.INTERACTION, 90, "state change " + newId + "[" + property + "]" + location, 9600, 4);
            break;
        }
        ArcaneClient.engine().recordAllowedBlockUpdate(pos, growth, automationChanged);
    }

    public static void onSectionBlockUpdate(ChunkDeltaUpdateS2CPacket packet) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        packet.visitUpdates((pos, state) -> PacketSignalBridge.onBlockUpdate(pos.toImmutable(), state));
    }

    public static void onBlockEntity(BlockPos pos, BlockEntityType<?> type, String source) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        String id = PacketSignalBridge.path(Registries.BLOCK_ENTITY_TYPE.getId(type));
        if (ScannerStorageFilter.isStoragePath(id)) return;
        ArcaneClient.engine().recordLive(pos, SignalCategory.BLOCK_ENTITY, PacketSignalBridge.blockEntityStrength(id) + 70, source + ": " + id);
    }

    public static void onBlockEvent(BlockEventS2CPacket packet) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        String id = PacketSignalBridge.path(Registries.BLOCK.getId(packet.getBlock()));
        if (ScannerStorageFilter.isStoragePath(id)) return;
        int strength = id.contains("piston") || id.contains("note_block") ? 140 : 65;
        ArcaneClient.engine().recordLive(packet.getPos(), SignalCategory.INTERACTION, strength, "block event: " + id);
        ArcaneClient.engine().recordAllowedBlockUpdate(packet.getPos(), null, true);
    }

    public static void onBlockBreaking(BlockBreakingProgressS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.world == null || packet.getProgress() <= 0 || client.player != null && packet.getEntityId() == client.player.getId()) {
            return;
        }
        Entity actor = client.world.getEntityById(packet.getEntityId());
        int strength = actor instanceof PlayerEntity ? 200 : 55;
        String who = actor instanceof PlayerEntity ? "other player" : "entity";
        ArcaneClient.engine().recordLive(packet.getPos(), SignalCategory.LIVE_ACTIVITY, strength, who + " breaking block" + PacketSignalBridge.hiddenSuffix(client, packet.getPos()), 6000, 2);
    }

    public static void onSound(PlaySoundS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null) {
            return;
        }
        BlockPos pos = BlockPos.ofFloored((double)packet.getX(), (double)packet.getY(), (double)packet.getZ());
        String id = packet.getSound().getKey().map(key -> key.getValue().getPath()).orElse("");
        if (PacketSignalBridge.nearLocalPlayer(client, pos)) {
            return;
        }
        if (INTERACTION_SOUNDS.contains(id) || id.contains("door.open") || id.contains("trapdoor.open") || id.contains("fence_gate.open")) {
            ArcaneClient.engine().recordLive(pos, SignalCategory.INTERACTION, 70, "server sound: " + id, 3600, 10);
        }
    }

    public static void onParticle(ParticleS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null) {
            return;
        }
        BlockPos pos = BlockPos.ofFloored((double)packet.getX(), (double)packet.getY(), (double)packet.getZ());
        if (PacketSignalBridge.nearLocalPlayer(client, pos)) {
            return;
        }
        ParticleType type = packet.getParameters().getType();
        String id = PacketSignalBridge.path(Registries.PARTICLE_TYPE.getId(type));
        if (id.contains("composter") || id.contains("wax_") || id.contains("campfire") || id.equals("happy_villager") || id.equals("shriek")) {
            ArcaneClient.engine().recordLive(pos, SignalCategory.LIVE_ACTIVITY, 50, "server particle: " + id, 2400, 20);
        }
    }

    public static void onLevelEvent(WorldEventS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || packet.isGlobal()) {
            return;
        }
        int type = packet.getEventId();
        if (type == 2001) {
            BlockState broken = Block.getStateFromRawId(packet.getData());
            String id = PacketSignalBridge.blockId(broken);
            if (GrowthTransitions.amethystRank(id) >= 0) {
                ArcaneClient.engine().recordAmethystBreak(packet.getPos(), id);
            }
            if (ActivityClassifier.isCobbledDeepslateTrailPath(id)) {
                ArcaneClient.engine().recordCobbledDeepslateHint(packet.getPos(), 90);
            }
        }
        if (PacketSignalBridge.nearLocalPlayer(client, packet.getPos())) {
            return;
        }
        if (type == 1030 || type == 1042 || type == 1044 || type == 1500 || type == 1502 || type == 2012 || type == 3003 || type == 3004 || type == 3005) {
            ArcaneClient.engine().recordLive(packet.getPos(), SignalCategory.INTERACTION, 75, "server level event " + type, 3600, 10);
        }
    }

    public static void onLightUpdate(LightUpdateS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.world == null || !ArcaneClient.engine().recordsConcealedLight()) {
            return;
        }
        if (client.player == null) {
            return;
        }
        int observerSectionY = ChunkSectionPos.getSectionCoord((int)client.player.getBlockPos().getY());
        if (!ArcaneClient.engine().hasScanBaseline(packet.getChunkX(), packet.getChunkZ(), observerSectionY)) {
            return;
        }
        if (client.player != null && Math.abs(client.player.getChunkPos().x - packet.getChunkX()) <= 1 && Math.abs(client.player.getChunkPos().z - packet.getChunkZ()) <= 1) {
            return;
        }
        int touchedSections = packet.getData().getInitedBlock().cardinality() + packet.getData().getUninitedBlock().cardinality();
        if (touchedSections == 0) {
            return;
        }
        int nonzeroBytes = 0;
        Iterator iterator = packet.getData().getBlockNibbles().iterator();
        while (iterator.hasNext()) {
            byte[] data;
            for (byte value : data = (byte[])iterator.next()) {
                if (value == 0) continue;
                ++nonzeroBytes;
            }
        }
        BlockPos position = new BlockPos(packet.getChunkX() * 16 + 8, client.world.getBottomY() + 32, packet.getChunkZ() * 16 + 8);
        int strength = EvidenceHeuristics.standaloneLightUpdate(touchedSections, nonzeroBytes);
        ArcaneClient.engine().recordLive(position, SignalCategory.LIGHT_LEAK, strength, "standalone block-light section update", 3600, 40);
    }

    public static void onEntitySpawn(EntitySpawnS2CPacket packet) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        String id = PacketSignalBridge.path(Registries.ENTITY_TYPE.getId(packet.getEntityType()));
        int strength = PacketSignalBridge.entityStrength(id);
        if (strength > 0) {
            BlockPos pos = BlockPos.ofFloored((double)packet.getX(), (double)packet.getY(), (double)packet.getZ());
            ArcaneClient.engine().recordLive(pos, SignalCategory.ENTITY, strength, "notable entity: " + id, 9600, 20);
        }
    }

    public static void onEntityChanged(int entityId, String reason) {
        MobEntity mob;
        boolean equipped;
        TameableEntity tame;
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.world == null) {
            return;
        }
        Entity entity = client.world.getEntityById(entityId);
        if (entity == null || entity == client.player) {
            return;
        }
        boolean playerOwned = entity instanceof TameableEntity && (tame = (TameableEntity)entity).isTamed();
        boolean bl = equipped = entity instanceof MobEntity && (mob = (MobEntity)entity).hasSaddleEquipped();
        if (playerOwned || equipped || entity.hasCustomName()) {
            String id = PacketSignalBridge.path(Registries.ENTITY_TYPE.getId(entity.getType()));
            ArcaneClient.engine().recordLive(entity.getBlockPos(), SignalCategory.ENTITY, 140, reason + ": managed " + id);
        }
    }

    public static void onEntityLink(EntityAttachS2CPacket packet) {
        MinecraftClient client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.world == null || packet.getHoldingEntityId() == 0) {
            return;
        }
        Entity source = client.world.getEntityById(packet.getAttachedEntityId());
        if (source != null) {
            ArcaneClient.engine().recordLive(source.getBlockPos(), SignalCategory.ENTITY, 160, "leashed entity");
        }
    }

    private static MinecraftClient clientOnMainThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.isOnThread() ? client : null;
    }

    private static boolean nearLocalPlayer(MinecraftClient client, BlockPos pos) {
        return client.player != null && client.player.getBlockPos().getSquaredDistance((Vec3i)pos) < 64.0;
    }

    private static String hiddenSuffix(MinecraftClient client, BlockPos pos) {
        return client.player != null && (double)pos.getY() < client.player.getY() - 24.0 ? " below masked depth" : "";
    }

    private static BlockPosition at(BlockPos pos) {
        return new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String blockId(BlockState state) {
        return PacketSignalBridge.path(Registries.BLOCK.getId(state.getBlock()));
    }

    private static String path(Identifier id) {
        return id == null ? "unknown" : id.getPath();
    }

    private static Object property(BlockState state, String name) {
        for (Map.Entry entry : state.getEntries().entrySet()) {
            if (!((Property)entry.getKey()).getName().equals(name)) continue;
            return entry.getValue();
        }
        return null;
    }

    private static boolean isPlayerFingerprint(String id, BlockState state) {
        if (id.contains("copper_golem_statue") || id.equals("respawn_anchor") || id.equals("scaffolding") || id.equals("frogspawn") || id.equals("torchflower_crop") || id.equals("pitcher_crop") || id.equals("nether_portal")) {
            return true;
        }
        Object persistent = PacketSignalBridge.property(state, "persistent");
        return id.endsWith("_leaves") && Boolean.TRUE.equals(persistent);
    }

    private static int blockEntityStrength(String id) {
        if (ScannerStorageFilter.isStoragePath(id)) {
            return 0;
        }
        if (id.equals("beacon")) {
            return 110;
        }
        if (id.equals("mob_spawner") || id.equals("spawner")) {
            return 55;
        }
        return 18;
    }

    private static int entityStrength(String id) {
        if (id.equals("player")) {
            return 200;
        }
        if (id.equals("copper_golem") || id.equals("armor_stand") || id.contains("item_frame") || id.equals("painting")) {
            return 150;
        }
        if (id.equals("allay") || id.equals("villager")) {
            return 35;
        }
        return 0;
    }
}
