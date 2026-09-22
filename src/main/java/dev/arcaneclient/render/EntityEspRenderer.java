package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.additions.cosmetics.NametagConfig;
import dev.arcaneclient.additions.cosmetics.NametagEquipment;
import dev.arcaneclient.additions.cosmetics.NametagLayout;
import dev.arcaneclient.additions.cosmetics.NametagText;
import dev.arcaneclient.esp.EspRanges;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Player, mob, projectile, crystal, tracer, name-tag, and safe-hole overlays. */
@Environment(EnvType.CLIENT)
public final class EntityEspRenderer {
    private static final VoxelShape HOLE_BOX = Shapes.box(0.08, 0.02, 0.08, 0.92, 0.18, 0.92);
    private static List<Target> targets = List.of();
    private static List<HoleTarget> holes = List.of();
    private static long lastEntityRefresh = Long.MIN_VALUE;
    private static long lastHoleRefresh = Long.MIN_VALUE;
    private static int renderedNameCount;
    private static int renderedEquipmentCount;
    private static double renderedLocalHeadwearClearance = Double.NaN;
    private static final Map<Entity, NametagEquipment> EQUIPMENT = new HashMap<>();
    private static ClientLevel trackedWorld;

    private EntityEspRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(EntityEspRenderer::render);
    }

    public static void reset() {
        targets = List.of();
        holes = List.of();
        lastEntityRefresh = Long.MIN_VALUE;
        lastHoleRefresh = Long.MIN_VALUE;
        EQUIPMENT.clear();
        renderedNameCount = 0;
        renderedEquipmentCount = 0;
        renderedLocalHeadwearClearance = Double.NaN;
        trackedWorld = null;
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        if (client.level == null || client.player == null) {
            reset();
            return;
        }
        if (trackedWorld != client.level) {
            reset();
            trackedWorld = client.level;
        }
        long time = client.level.getGameTime();
        if (lastEntityRefresh == Long.MIN_VALUE || time - lastEntityRefresh >= 5 || time < lastEntityRefresh) {
            lastEntityRefresh = time;
            refreshEntities(client, config);
        }
        if (lastHoleRefresh == Long.MIN_VALUE || time - lastHoleRefresh >= 10 || time < lastHoleRefresh) {
            lastHoleRefresh = time;
            refreshHoles(client, config);
        }
    }

    private static void refreshEntities(Minecraft client, ArcaneConfig config) {
        if (!config.playerEsp && !config.mobEsp && !config.projectileEsp && !config.crystalEsp
            && !config.entityTracers && !config.nametags) {
            targets = List.of();
            EQUIPMENT.clear();
            return;
        }
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            targets = List.of();
            EQUIPMENT.clear();
            return;
        }
        int maximumRange = config.nametags ? Math.max(config.entityEspRange, config.nametagRange) : config.entityEspRange;
        double maxDistance = maximumRange * (double)maximumRange;
        double entityRange = config.entityEspRange * (double)config.entityEspRange;
        double nameRange = config.nametagRange * (double)config.nametagRange;
        ArrayList<Target> refreshed = new ArrayList<>();
        Set<Entity> equippedTargets = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            double distanceSquared = EspRanges.horizontalDistanceSquared(entity.getX(), entity.getZ(), camera.getX(), camera.getZ());
            boolean self = entity == client.player || FreecamController.isVisualBody(entity);
            if (entity.isRemoved() || distanceSquared > maxDistance) continue;
            if (entity == client.player && FreecamController.isActive()) continue;
            int espColor = !self && entity != camera && distanceSquared <= entityRange ? targetColor(entity, config) : 0;
            boolean nameOnly = config.nametags && nametagEligible(client, entity, config.nametagAdditions)
                && entity.distanceToSqr(camera) <= nameRange;
            boolean tracerOnly = !self && entity != camera && config.entityTracers && traceable(entity) && distanceSquared <= entityRange;
            if (espColor == 0 && !nameOnly && !tracerOnly) continue;
            int color = espColor != 0 ? espColor : nameOnly ? config.nametagColor : defaultColor(entity, config);
            AABB box = entity.getBoundingBox();
            VoxelShape shape = Shapes.create(box.move(-entity.getX(), -entity.getY(), -entity.getZ()));
            if (nameOnly && entity instanceof Player) {
                equippedTargets.add(entity);
                if (EQUIPMENT.size() < 128) EQUIPMENT.computeIfAbsent(entity, ignored -> new NametagEquipment());
            }
            refreshed.add(new Target(entity, shape, color, nameOnly,
                box.maxY - entity.getY() + 0.3, espColor != 0, tracerOnly));
            if (refreshed.size() >= 512) break;
        }
        targets = List.copyOf(refreshed);
        EQUIPMENT.keySet().retainAll(equippedTargets);
    }

    private static boolean nametagEligible(Minecraft client, Entity entity, NametagConfig c) {
        boolean self = entity == client.player || FreecamController.isVisualBody(entity);
        if (self) return c.self && (FreecamController.isActive() || !client.options.getCameraType().isFirstPerson());
        return entity instanceof Player ? c.players : entity instanceof ItemEntity && c.items;
    }

    private static String nameLabel(Entity entity, ArcaneConfig config, Vec3 camera) {
        int distance = (int)Math.round(Math.sqrt(entity.distanceToSqr(camera)));
        if (entity instanceof ItemEntity item && !item.getItem().isEmpty()) {
            return NametagText.item(config.nametagAdditions, item.getItem().getHoverName().getString(), item.getItem().getCount(), distance);
        }
        if (entity instanceof LivingEntity living) {
            Minecraft client = Minecraft.getInstance();
            Entity badgeOwner = FreecamController.isVisualBody(entity) && client.player != null ? client.player : entity;
            boolean badge = dev.arcaneclient.additions.presence.ArcanePresence.hasBadge(badgeOwner.getUUID());
            return NametagText.player(config.nametagAdditions, entity.getName().getString(), config.streamerMode, badge,
                Math.round(living.getHealth() + living.getAbsorptionAmount()), distance);
        }
        return "";
    }

    private static int targetColor(Entity entity, ArcaneConfig config) {
        if (entity instanceof Player) return config.playerEsp ? config.playerEspColor : 0;
        if (entity instanceof EndCrystal) return config.crystalEsp ? config.crystalEspColor : 0;
        if (entity instanceof Projectile) return config.projectileEsp ? config.projectileEspColor : 0;
        if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) return config.mobEsp ? config.mobEspColor : 0;
        return 0;
    }

    private static boolean traceable(Entity entity) {
        return entity instanceof Player || entity instanceof LivingEntity && !(entity instanceof ArmorStand)
            || entity instanceof Projectile || entity instanceof EndCrystal;
    }

    private static int defaultColor(Entity entity, ArcaneConfig config) {
        if (entity instanceof Player) return config.playerEspColor;
        if (entity instanceof EndCrystal) return config.crystalEspColor;
        if (entity instanceof Projectile) return config.projectileEspColor;
        return config.mobEspColor;
    }

    private static void refreshHoles(Minecraft client, ArcaneConfig config) {
        if (!config.holeEsp) {
            holes = List.of();
            return;
        }
        BlockPos center = client.player.blockPosition();
        int radius = config.holeEspRange;
        ArrayList<HoleTarget> refreshed = new ArrayList<>();
        for (int y = -2; y <= 2; y++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    BlockPos pos = center.offset(dx, y, dz);
                    if (!isSafeHole(client, pos)) continue;
                    refreshed.add(new HoleTarget(pos.immutable(), config.holeEspColor));
                    if (refreshed.size() >= 128) {
                        holes = List.copyOf(refreshed);
                        return;
                    }
                }
            }
        }
        holes = List.copyOf(refreshed);
    }

    private static boolean isSafeHole(Minecraft client, BlockPos pos) {
        if (!client.level.getBlockState(pos).isAir() || !client.level.getBlockState(pos.above()).isAir()) return false;
        return safe(client.level.getBlockState(pos.below()))
            && safe(client.level.getBlockState(pos.north()))
            && safe(client.level.getBlockState(pos.south()))
            && safe(client.level.getBlockState(pos.east()))
            && safe(client.level.getBlockState(pos.west()));
    }

    private static boolean safe(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
    }

    private static void render(LevelRenderContext context) {
        renderedNameCount = 0;
        renderedLocalHeadwearClearance = Double.NaN;
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (!canRender(client, context)) return;
        if (targets.isEmpty() && holes.isEmpty()) return;
        Vec3 camera = Render263.camera(context);
        float partialTick = Render263.partialTick(context);
        Render263.Batch batch = Render263.batch(context);
        for (Target target : targets) {
            if (target.entity().isRemoved()) continue;
            Vec3 pos = target.entity().getPosition(partialTick);
            if (target.outline()) {
                batch.outline(target.shape(), pos.x, pos.y, pos.z, target.color(), 1.8f, true);
            }
            if (target.tracer()) {
                batch.line(camera.x, camera.y, camera.z, pos.x, pos.y + 0.5, pos.z,
                    target.color(), 1.15f, true);
            }
        }
        for (HoleTarget hole : holes) {
            BlockPos pos = hole.pos();
            batch.outline(HOLE_BOX, pos.getX(), pos.getY(), pos.getZ(), hole.color(), 1.8f, true);
        }
        batch.submit();
        // Text rendering may switch the shared immediate buffer to another layer, which
        // invalidates the line consumer. Draw labels only after every outline is complete.
        for (Target target : targets) {
            if (config.nametags && target.name() && !target.entity().isRemoved()
                && nametagEligible(client, target.entity(), config.nametagAdditions)) {
                String label = nameLabel(target.entity(), config, camera);
                if (label.isEmpty()) continue;
                drawName(context, label, nameAnchor(target, partialTick), config.nametagColor);
                renderedNameCount++;
            }
        }
    }

    private static boolean canRender(Minecraft client, LevelRenderContext context) {
        return client.level != null && trackedWorld == client.level && client.player != null && context.poseStack() != null
            && !ArcaneVisibility.overlaysHidden() && !ArcaneSettingsScreen.isOpen(client);
    }

    private static void renderEquipment(LevelRenderContext context) {
        renderedEquipmentCount = 0;
        // Equipment icons require 26.3 item render-state extraction. Names remain functional;
        // icons are intentionally omitted until that state adapter is available.
    }

    private static Vec3 nameAnchor(Target target, float partialTick) {
        return target.entity().getPosition(partialTick).add(0, target.nameHeight(), 0);
    }

    private static void drawName(LevelRenderContext context, String name, Vec3 pos, int color) {
        Minecraft client = Minecraft.getInstance();
        Font font = ArcaneFont.renderer(client);
        NametagConfig c = ArcaneClient.config().nametagAdditions;
        float scale = 0.025f * Math.clamp(c.scale, 50, 200) / 100.0f;
        Render263.text(context, font, name, pos, scale, color, true);
    }

    public static int targetCount() {
        return targets.size() + holes.size();
    }

    public static int tracerTargetCount() {
        int count = 0;
        for (Target target : targets) {
            if (target.tracer()) count++;
        }
        return count;
    }

    public static int renderedNameCount() { return renderedNameCount; }
    public static int renderedEquipmentCount() { return renderedEquipmentCount; }
    public static double renderedLocalHeadwearClearance() { return renderedLocalHeadwearClearance; }

    public static List<String> nameLabels() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return List.of();
        Entity camera = client.getCameraEntity();
        Vec3 cameraPos = camera == null ? Vec3.ZERO : new Vec3(camera.getX(), camera.getY(), camera.getZ());
        return targets.stream().filter(Target::name)
            .map(target -> nameLabel(target.entity(), ArcaneClient.config(), cameraPos))
            .filter(label -> !label.isEmpty()).toList();
    }

    private record Target(Entity entity, VoxelShape shape, int color, boolean name, double nameHeight, boolean outline, boolean tracer) {
    }

    private record HoleTarget(BlockPos pos, int color) {
    }
}
