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
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

@Environment(value=EnvType.CLIENT)
public final class PacketSignalBridge {
    private static final Set<String> INTERACTION_SOUNDS = Set.of("block.piston.extend", "block.piston.contract", "block.lever.click", "block.stone_button.click_on", "block.stone_button.click_off", "block.wooden_button.click_on", "block.wooden_button.click_off", "block.note_block.harp", "block.respawn_anchor.charge", "block.beacon.activate", "block.anvil.use", "block.grindstone.use", "block.smithing_table.use", "block.copper_bulb.turn_on", "block.copper_bulb.turn_off");
    private static final Set<String> INTERACTION_PROPERTIES = Set.of("powered", "open", "lit", "charges", "bites", "note", "delay", "mode", "disarmed", "occupied");

    private PacketSignalBridge() {
    }

    public static void onChunkData(ClientboundLevelChunkWithLightPacket packet) {
        // ClientChunkEvents queues the loaded chunk. No packet light/entity evidence is scanned.
    }

    public static void onBlockUpdate(BlockPos pos, BlockState incoming) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.level == null) {
            return;
        }
        BlockState old = client.level.getBlockState(pos);
        if (old.equals((Object)incoming)) {
            return;
        }
        String oldId = PacketSignalBridge.blockId(old);
        String newId = PacketSignalBridge.blockId(incoming);
        if (old.hasBlockEntity() || incoming.hasBlockEntity()) {
            return;
        }
        ArcaneClient.engine().recordAmethystState(pos, oldId, newId);
        ArcaneClient.engine().recordCobbledDeepslateState(pos, oldId, newId);
        if (dev.arcaneclient.scan.GrownBlocks.matches(old) || dev.arcaneclient.scan.GrownBlocks.matches(incoming)
            || ArcaneClient.config().tunnelEsp || ArcaneClient.config().accessTrailEsp || ArcaneClient.config().amethystEsp) {
            ArcaneClient.engine().recordAllowedBlockUpdate(pos, null, false);
        }
    }

    public static void onSectionBlockUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        packet.runUpdates((pos, state) -> PacketSignalBridge.onBlockUpdate(pos.immutable(), state));
    }

    public static void onBlockEntity(BlockPos pos, BlockEntityType<?> type, String source) {
        // ESP reads the world's block-entity index. No scanner evidence is emitted.
    }

    public static void onBlockEvent(ClientboundBlockEventPacket packet) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        String id = PacketSignalBridge.path(BuiltInRegistries.BLOCK.getKey(packet.getBlock()));
        if (ScannerStorageFilter.isStoragePath(id)) return;
        int strength = id.contains("piston") || id.contains("note_block") ? 140 : 65;
        ArcaneClient.engine().recordLive(packet.getPos(), SignalCategory.INTERACTION, strength, "block event: " + id);
    }

    public static void onBlockBreaking(ClientboundBlockDestructionPacket packet) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.level == null || packet.getProgress() <= 0 || client.player != null && packet.getId() == client.player.getId()) {
            return;
        }
        Entity actor = client.level.getEntity(packet.getId());
        int strength = actor instanceof Player ? 200 : 55;
        String who = actor instanceof Player ? "other player" : "entity";
        ArcaneClient.engine().recordLive(packet.getPos(), SignalCategory.LIVE_ACTIVITY, strength, who + " breaking block" + PacketSignalBridge.hiddenSuffix(client, packet.getPos()), 6000, 2);
    }

    public static void onSound(ClientboundSoundPacket packet) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(packet.getX(), packet.getY(), packet.getZ());
        String id = packet.getSound().unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        if (PacketSignalBridge.nearLocalPlayer(client, pos)) {
            return;
        }
        if (INTERACTION_SOUNDS.contains(id) || id.contains("door.open") || id.contains("trapdoor.open") || id.contains("fence_gate.open")) {
            ArcaneClient.engine().recordLive(pos, SignalCategory.INTERACTION, 70, "server sound: " + id, 3600, 10);
        }
    }

    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(packet.x(), packet.y(), packet.z());
        if (PacketSignalBridge.nearLocalPlayer(client, pos)) {
            return;
        }
        ParticleType type = packet.particle().getType();
        String id = PacketSignalBridge.path(BuiltInRegistries.PARTICLE_TYPE.getKey(type));
        if (id.contains("composter") || id.contains("wax_") || id.contains("campfire") || id.equals("happy_villager") || id.equals("shriek")) {
            ArcaneClient.engine().recordLive(pos, SignalCategory.LIVE_ACTIVITY, 50, "server particle: " + id, 2400, 20);
        }
    }

    public static void onLevelEvent(ClientboundLevelEventPacket packet) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null || packet.isGlobalEvent()) {
            return;
        }
        int type = packet.getType();
        if (type == 2001) {
            BlockState broken = Block.stateById(packet.getData());
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

    public static void onLightUpdate(ClientboundLightUpdatePacket packet) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.level == null || !ArcaneClient.engine().recordsConcealedLight()) {
            return;
        }
        if (client.player == null) {
            return;
        }
        int observerSectionY = SectionPos.blockToSectionCoord((int)client.player.blockPosition().getY());
        int chunkX = packet.x();
        int chunkZ = packet.z();
        if (!ArcaneClient.engine().hasScanBaseline(chunkX, chunkZ, observerSectionY)) {
            return;
        }
        if (client.player != null && Math.abs(client.player.chunkPosition().x() - chunkX) <= 1 && Math.abs(client.player.chunkPosition().z() - chunkZ) <= 1) {
            return;
        }
        var lightData = packet.lightData();
        int touchedSections = lightData.blockYMask().cardinality() + lightData.emptyBlockYMask().cardinality();
        if (touchedSections == 0) {
            return;
        }
        int nonzeroBytes = 0;
        Iterator<byte[]> iterator = lightData.blockUpdates().iterator();
        while (iterator.hasNext()) {
            byte[] data;
            for (byte value : data = iterator.next()) {
                if (value == 0) continue;
                ++nonzeroBytes;
            }
        }
        BlockPos position = new BlockPos(chunkX * 16 + 8, client.level.getMinY() + 32, chunkZ * 16 + 8);
        int strength = EvidenceHeuristics.standaloneLightUpdate(touchedSections, nonzeroBytes);
        ArcaneClient.engine().recordLive(position, SignalCategory.LIGHT_LEAK, strength, "standalone block-light section update", 3600, 40);
    }

    public static void onEntitySpawn(ClientboundAddEntityPacket packet) {
        if (PacketSignalBridge.clientOnMainThread() == null) {
            return;
        }
        String id = PacketSignalBridge.path(BuiltInRegistries.ENTITY_TYPE.getKey(packet.getType()));
        int strength = PacketSignalBridge.entityStrength(id);
        if (strength > 0) {
            BlockPos pos = BlockPos.containing((double)packet.getX(), (double)packet.getY(), (double)packet.getZ());
            ArcaneClient.engine().recordLive(pos, SignalCategory.ENTITY, strength, "notable entity: " + id, 9600, 20);
        }
    }

    public static void onEntityChanged(int entityId, String reason) {
        Mob mob;
        boolean equipped;
        TamableAnimal tame;
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.level == null) {
            return;
        }
        Entity entity = client.level.getEntity(entityId);
        if (entity == null || entity == client.player) {
            return;
        }
        boolean playerOwned = entity instanceof TamableAnimal && (tame = (TamableAnimal)entity).isTame();
        boolean bl = equipped = entity instanceof Mob && (mob = (Mob)entity).isSaddled();
        if (playerOwned || equipped || entity.hasCustomName()) {
            String id = PacketSignalBridge.path(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            ArcaneClient.engine().recordLive(entity.blockPosition(), SignalCategory.ENTITY, 140, reason + ": managed " + id);
        }
    }

    public static void onEntityLink(ClientboundSetEntityLinkPacket packet) {
        Minecraft client = PacketSignalBridge.clientOnMainThread();
        if (client == null || client.level == null || packet.getDestId() == 0) {
            return;
        }
        Entity source = client.level.getEntity(packet.getSourceId());
        if (source != null) {
            ArcaneClient.engine().recordLive(source.blockPosition(), SignalCategory.ENTITY, 160, "leashed entity");
        }
    }

    private static Minecraft clientOnMainThread() {
        Minecraft client = Minecraft.getInstance();
        return client.isSameThread() ? client : null;
    }

    private static boolean nearLocalPlayer(Minecraft client, BlockPos pos) {
        return client.player != null && client.player.blockPosition().distSqr((Vec3i)pos) < 64.0;
    }

    private static String hiddenSuffix(Minecraft client, BlockPos pos) {
        return client.player != null && (double)pos.getY() < client.player.getY() - 24.0 ? " below masked depth" : "";
    }

    private static BlockPosition at(BlockPos pos) {
        return new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String blockId(BlockState state) {
        return PacketSignalBridge.path(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static String path(Identifier id) {
        return id == null ? "unknown" : id.getPath();
    }

    private static Object property(BlockState state, String name) {
        Iterator<Property.Value<?>> values = state.getValues().iterator();
        while (values.hasNext()) {
            Property.Value<?> entry = values.next();
            if (!entry.property().getName().equals(name)) continue;
            return entry.value();
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
