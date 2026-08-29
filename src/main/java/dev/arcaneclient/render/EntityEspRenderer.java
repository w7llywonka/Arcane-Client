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

    private EntityEspRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(EntityEspRenderer::render);
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        if (client.world == null || client.player == null) {
            targets = List.of();
            holes = List.of();
            return;
        }
        long time = client.world.getTime();
        if (time - lastEntityRefresh >= 5 || time < lastEntityRefresh) {
            lastEntityRefresh = time;
            refreshEntities(client, config);
        }
        if (time - lastHoleRefresh >= 10 || time < lastHoleRefresh) {
            lastHoleRefresh = time;
            refreshHoles(client, config);
        }
    }

    private static void refreshEntities(MinecraftClient client, ArcaneConfig config) {
        if (!config.playerEsp && !config.mobEsp && !config.projectileEsp && !config.crystalEsp) {
            targets = List.of();
            return;
        }
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            targets = List.of();
            return;
        }
        double maxDistance = config.entityEspRange * (double) config.entityEspRange;
        ArrayList<Target> refreshed = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity == camera || entity.isRemoved()
                || EspRanges.horizontalDistanceSquared(entity.getX(), entity.getZ(), camera.getX(), camera.getZ()) > maxDistance) continue;
            int color = targetColor(entity, config);
            if (color == 0) continue;
            Box box = entity.getBoundingBox();
            VoxelShape shape = VoxelShapes.cuboid(box.offset(-entity.getX(), -entity.getY(), -entity.getZ()));
            String name = null;
            if (config.entityNameTags && entity instanceof LivingEntity) {
                name = config.streamerMode && entity instanceof PlayerEntity ? "PLAYER" : entity.getName().getString();
            }
            refreshed.add(new Target(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), shape, color, name));
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
            VertexRendering.drawOutline(matrices, lines, target.shape(), x, y, z, target.color(), 1.8f);
            if (config.entityTracers) {
                TracerLines.draw(matrices.peek(), lines, forward.x() * 0.25, forward.y() * 0.25, forward.z() * 0.25, x, y + 0.5, z, target.color(), 1.15f);
            }
            if (target.name() != null) drawName(context, target.name(), pos, camera, target.color());
        }
        for (HoleTarget hole : holes) {
            BlockPos pos = hole.pos();
            VertexRendering.drawOutline(matrices, lines, HOLE_BOX, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, hole.color(), 1.8f);
        }
    }

    private static void drawName(WorldRenderContext context, String name, Vec3d pos, Vec3d camera, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = ArcaneFont.renderer(client);
        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y + 2.2 - camera.y, pos.z - camera.z);
        matrices.multiply((Quaternionfc) context.worldState().cameraRenderState.orientation);
        matrices.scale(-0.025f, -0.025f, 0.025f);
        font.draw(name, -font.getWidth(name) / 2.0f, 0.0f, color, false, matrices.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000, 0xF000F0);
        matrices.pop();
    }

    public static int targetCount() {
        return targets.size() + holes.size();
    }

    private record Target(Vec3d pos, VoxelShape shape, int color, String name) {
    }

    private record HoleTarget(BlockPos pos, int color) {
    }
}
