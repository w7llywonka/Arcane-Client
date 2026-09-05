package dev.arcaneclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.esp.EspRanges;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

/** Player, mob, projectile, crystal, tracer, name-tag, and safe-hole overlays. */
@Environment(EnvType.CLIENT)
public final class EntityEspRenderer {
    private static final VoxelShape HOLE_BOX = VoxelShapes.cuboid(0.08, 0.02, 0.08, 0.92, 0.18, 0.92);
    private static final RenderPipeline LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(ArcaneClient.id("pipeline/entity_esp"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    );
    private static final RenderLayer LINE_TYPE = RenderLayer.of("arcaneclient_entity_esp", RenderSetup.builder(LINES).build());
    private static List<Target> targets = List.of();
    private static List<HoleTarget> holes = List.of();
    private static long lastEntityRefresh = Long.MIN_VALUE;
    private static long lastHoleRefresh = Long.MIN_VALUE;
    private static int renderedNameCount;

    private EntityEspRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(EntityEspRenderer::render);
    }

    public static void reset() {
        targets = List.of();
        holes = List.of();
        lastEntityRefresh = Long.MIN_VALUE;
        lastHoleRefresh = Long.MIN_VALUE;
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        if (client.world == null || client.player == null) {
            reset();
            return;
        }
        long time = client.world.getTime();
        if (lastEntityRefresh == Long.MIN_VALUE || time - lastEntityRefresh >= 5 || time < lastEntityRefresh) {
            lastEntityRefresh = time;
            refreshEntities(client, config);
        }
        if (lastHoleRefresh == Long.MIN_VALUE || time - lastHoleRefresh >= 10 || time < lastHoleRefresh) {
            lastHoleRefresh = time;
            refreshHoles(client, config);
        }
    }

    private static void refreshEntities(MinecraftClient client, ArcaneConfig config) {
        if (!config.playerEsp && !config.mobEsp && !config.projectileEsp && !config.crystalEsp
            && !config.entityTracers && !config.nametags) {
            targets = List.of();
            return;
        }
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            targets = List.of();
            return;
        }
        int maximumRange = config.nametags ? Math.max(config.entityEspRange, config.nametagRange) : config.entityEspRange;
        double maxDistance = maximumRange * (double)maximumRange;
        double entityRange = config.entityEspRange * (double)config.entityEspRange;
        double nameRange = config.nametagRange * (double)config.nametagRange;
        ArrayList<Target> refreshed = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            double distanceSquared = EspRanges.horizontalDistanceSquared(entity.getX(), entity.getZ(), camera.getX(), camera.getZ());
            if (entity == client.player || entity == camera || entity.isRemoved() || distanceSquared > maxDistance) continue;
            int espColor = distanceSquared <= entityRange ? targetColor(entity, config) : 0;
            boolean nameOnly = config.nametags && (entity instanceof PlayerEntity || entity instanceof ItemEntity)
                && entity.squaredDistanceTo(camera) <= nameRange;
            boolean tracerOnly = config.entityTracers && traceable(entity) && distanceSquared <= entityRange;
            if (espColor == 0 && !nameOnly && !tracerOnly) continue;
            int color = espColor != 0 ? espColor : nameOnly ? config.nametagColor : defaultColor(entity, config);
            Box box = entity.getBoundingBox();
            VoxelShape shape = VoxelShapes.cuboid(box.offset(-entity.getX(), -entity.getY(), -entity.getZ()));
            String name = null;
            if (nameOnly && entity instanceof ItemEntity item && !item.getStack().isEmpty()) {
                name = item.getStack().getName().getString() + " ×" + item.getStack().getCount();
            } else if (nameOnly && entity instanceof LivingEntity living) {
                String entityName = config.streamerMode && entity instanceof PlayerEntity ? "PLAYER" : entity.getName().getString();
                int health = Math.max(0, Math.round(living.getHealth() + living.getAbsorptionAmount()));
                int distance = (int)Math.round(Math.sqrt(entity.squaredDistanceTo(camera)));
                name = entityName + "  " + health + " HP  " + distance + "m";
            }
            refreshed.add(new Target(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), shape, color, name,
                box.maxY - entity.getY() + 0.3, espColor != 0, tracerOnly));
            if (refreshed.size() >= 512) break;
        }
        targets = List.copyOf(refreshed);
    }

    private static int targetColor(Entity entity, ArcaneConfig config) {
        if (entity instanceof PlayerEntity) return config.playerEsp ? config.playerEspColor : 0;
        if (entity instanceof EndCrystalEntity) return config.crystalEsp ? config.crystalEspColor : 0;
        if (entity instanceof ProjectileEntity) return config.projectileEsp ? config.projectileEspColor : 0;
        if (entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity)) return config.mobEsp ? config.mobEspColor : 0;
        return 0;
    }

    private static boolean traceable(Entity entity) {
        return entity instanceof PlayerEntity || entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity)
            || entity instanceof ProjectileEntity || entity instanceof EndCrystalEntity;
    }

    private static int defaultColor(Entity entity, ArcaneConfig config) {
        if (entity instanceof PlayerEntity) return config.playerEspColor;
        if (entity instanceof EndCrystalEntity) return config.crystalEspColor;
        if (entity instanceof ProjectileEntity) return config.projectileEspColor;
        return config.mobEspColor;
    }

    private static void refreshHoles(MinecraftClient client, ArcaneConfig config) {
        if (!config.holeEsp) {
            holes = List.of();
            return;
        }
        BlockPos center = client.player.getBlockPos();
        int radius = config.holeEspRange;
        ArrayList<HoleTarget> refreshed = new ArrayList<>();
        for (int y = -2; y <= 2; y++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    BlockPos pos = center.add(dx, y, dz);
                    if (!isSafeHole(client, pos)) continue;
                    refreshed.add(new HoleTarget(pos.toImmutable(), config.holeEspColor));
                    if (refreshed.size() >= 128) {
                        holes = List.copyOf(refreshed);
                        return;
                    }
                }
            }
        }
        holes = List.copyOf(refreshed);
    }

    private static boolean isSafeHole(MinecraftClient client, BlockPos pos) {
        if (!client.world.getBlockState(pos).isAir() || !client.world.getBlockState(pos.up()).isAir()) return false;
        return safe(client.world.getBlockState(pos.down()))
            && safe(client.world.getBlockState(pos.north()))
            && safe(client.world.getBlockState(pos.south()))
            && safe(client.world.getBlockState(pos.east()))
            && safe(client.world.getBlockState(pos.west()));
    }

    private static boolean safe(BlockState state) {
        return state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN);
    }

    private static void render(WorldRenderContext context) {
        renderedNameCount = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client) || context.matrices() == null) return;
        if (targets.isEmpty() && holes.isEmpty()) return;
        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = context.consumers().getBuffer(LINE_TYPE);
        Vector3fc forward = client.gameRenderer.getCamera().getHorizontalPlane();
        for (Target target : targets) {
            Vec3d pos = target.pos();
            double x = pos.x - camera.x;
            double y = pos.y - camera.y;
            double z = pos.z - camera.z;
            if (target.outline()) {
                VertexRendering.drawOutline(matrices, lines, target.shape(), x, y, z, target.color(), 1.8f);
            }
            if (target.tracer()) {
                TracerLines.draw(matrices.peek(), lines, forward.x() * 0.25, forward.y() * 0.25, forward.z() * 0.25, x, y + 0.5, z, target.color(), 1.15f);
            }
        }
        for (HoleTarget hole : holes) {
            BlockPos pos = hole.pos();
            VertexRendering.drawOutline(matrices, lines, HOLE_BOX, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, hole.color(), 1.8f);
        }
        // Text rendering may switch the shared immediate buffer to another layer, which
        // invalidates the line consumer. Draw labels only after every outline is complete.
        for (Target target : targets) {
            if (config.nametags && target.name() != null) {
                drawName(context, target.name(), target.pos().add(0, target.nameHeight(), 0), camera, config.nametagColor);
                renderedNameCount++;
            }
        }
    }

    private static void drawName(WorldRenderContext context, String name, Vec3d pos, Vec3d camera, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = ArcaneFont.renderer(client);
        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply((Quaternionfc) context.worldState().cameraRenderState.orientation);
        // Match vanilla's label orientation; negative X reverses the text face and culls it.
        matrices.scale(0.025f, -0.025f, 0.025f);
        var label = ArcaneFont.text(name);
        font.draw(label, -font.getWidth(label) / 2.0f, 0.0f, color, false, matrices.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000, 0xF000F0);
        matrices.pop();
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

    public static List<String> nameLabels() {
        return targets.stream().map(Target::name).filter(java.util.Objects::nonNull).toList();
    }

    private record Target(Vec3d pos, VoxelShape shape, int color, String name, double nameHeight, boolean outline, boolean tracer) {
    }

    private record HoleTarget(BlockPos pos, int color) {
    }
}
