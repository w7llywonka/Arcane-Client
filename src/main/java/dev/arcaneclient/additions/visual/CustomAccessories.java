package dev.arcaneclient.additions.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.additions.cosmetics.TrailHistory;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** Original client-only meshes attached to the same submitted body state vanilla is drawing. */
public final class CustomAccessories {
    private static final TrailHistory TRAIL = new TrailHistory();
    private static Entity trackedBody;
    private static double lastX, lastY, lastZ;
    private static float capeBend, previousCapeBend, capeVelocity;
    private static float capeSway, previousCapeSway;
    private static int ticks;
    private static int submittedLines;
    private static int renderedCapeQuads;
    private static int renderedTrailSegments;
    private static int renderedAuraSegments;

    private CustomAccessories() { }

    static String styleName(int style) {
        return switch (style) {
            case 1 -> "Crown";
            case 2 -> "Horns";
            default -> "Halo";
        };
    }

    static String capeStyleName(int style) {
        return switch (style) { case 1 -> "Chevron"; case 2 -> "Constellation"; default -> "Arcane"; };
    }

    static String trailStyleName(int style) {
        return switch (style) { case 1 -> "Twin streams"; case 2 -> "Stardust"; default -> "Ribbon"; };
    }

    static String auraStyleName(int style) {
        return switch (style) { case 1 -> "Runes"; case 2 -> "Pulse"; default -> "Orbit"; };
    }

    static void reset() {
        beginFrame();
        TRAIL.clear();
        trackedBody = null;
        lastX = lastY = lastZ = 0;
        capeBend = previousCapeBend = capeVelocity = 0;
        capeSway = previousCapeSway = 0;
        ticks = 0;
    }

    static void beginFrame() {
        submittedLines = renderedCapeQuads = renderedTrailSegments = renderedAuraSegments = 0;
    }

    public static int renderedCapeQuads() { return renderedCapeQuads; }
    public static int renderedTrailSegments() { return renderedTrailSegments; }
    public static int renderedAuraSegments() { return renderedAuraSegments; }
    public static int trailSampleCount() { return TRAIL.size(); }

    static void tick(Minecraft client, VisualAdditionsConfig c, boolean hidden) {
        Entity body = FreecamController.isActive() ? FreecamController.visualBodyEntity() : client.player;
        if (!c.customAccessories || hidden || body == null || body.isRemoved()
            || body.isInvisibleTo(client.player) || client.player.isSpectator()
            || !FreecamController.isActive() && client.options.getCameraType().isFirstPerson()) {
            reset();
            return;
        }
        if (trackedBody != body) {
            reset();
            trackedBody = body;
            lastX = body.getX(); lastY = body.getY(); lastZ = body.getZ();
        }
        double dx = body.getX() - lastX, dy = body.getY() - lastY, dz = body.getZ() - lastZ;
        if (dx * dx + dy * dy + dz * dz > 64) {
            TRAIL.clear();
            capeBend = capeVelocity = capeSway = 0;
            dx = dy = dz = 0;
        }
        previousCapeBend = capeBend;
        previousCapeSway = capeSway;
        float target = c.capePhysics ? (float)Math.min(0.62, Math.hypot(dx, dz) * 1.7 + Math.abs(dy) * 0.35) : 0;
        capeVelocity = (capeVelocity + (target - capeBend) * 0.24f) * 0.68f;
        capeBend = Math.clamp(capeBend + capeVelocity, 0, 0.7f);
        double yaw = Math.toRadians(body.getYRot());
        float sideways = (float)(dx * Math.cos(yaw) + dz * Math.sin(yaw));
        capeSway += ((c.capePhysics ? Math.clamp(sideways * 0.65f, -0.18f, 0.18f) : 0) - capeSway) * 0.28f;
        lastX = body.getX(); lastY = body.getY(); lastZ = body.getZ();
        ticks++;
        if (c.accessoryTrail) TRAIL.sample(lastX, lastY + 0.65, lastZ, c.trailLength);
        else TRAIL.clear();
    }

    /** Used by the cape feature mixin: ordinary/remote capes remain entirely vanilla. */
    public static boolean replacesVanillaCape(AvatarRenderState state) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || ArcaneClient.config() == null || ArcaneVisibility.overlaysHidden()
            || ArcaneSettingsScreen.isOpen(client)) return false;
        VisualAdditionsConfig c = ArcaneClient.config().visualAdditions;
        Entity body = FreecamController.isActive() ? FreecamController.visualBodyEntity() : client.player;
        return c.customAccessories && c.accessoryCape && body != null && state.id == body.getId()
            && wearable(state) && !state.chestEquipment.has(DataComponents.GLIDER);
    }

    private static boolean wearable(AvatarRenderState state) {
        return !state.isInvisible && !state.isSpectator && state.deathTime <= 0 && !state.isUpsideDown
            && !state.isFallFlying && !state.isVisuallySwimming && !state.hasPose(Pose.SLEEPING)
            && !state.hasPose(Pose.SWIMMING);
    }

    static void render(Minecraft client, LevelRenderContext context, VisualAdditionsConfig config) {
        Entity body = FreecamController.isActive() ? FreecamController.visualBodyEntity() : client.player;
        if (body == null || body.isRemoved() || body.isInvisibleTo(client.player)) return;
        if (body == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()
            && !FreecamController.isActive()) return;
        AvatarRenderState state = null;
        for (EntityRenderState candidate : context.levelState().entityRenderStates) {
            if (candidate instanceof AvatarRenderState player && player.id == body.getId()) {
                state = player;
                break;
            }
        }
        // Don't leave a floating accessory when vanilla hides/culls the body or changes its pose.
        if (state == null || !wearable(state)) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        double headY = state.y + state.eyeHeight + 0.23 * state.scale;
        double cameraDx = state.x - camera.x, cameraDy = headY - camera.y, cameraDz = state.z - camera.z;
        if (cameraDx * cameraDx + cameraDy * cameraDy + cameraDz * cameraDz < 0.25) return;
        PoseStack matrices = context.poseStack();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = ticks + partial;
        if (config.accessoryCape && !state.chestEquipment.has(DataComponents.GLIDER)) {
            cape(context, state, config, camera, partial, time);
        }
        if (config.accessoryTrail && config.trailStyle == 0) ribbon(context, state, config, camera, partial);
        if (config.accessoryTrail && config.trailStyle != 0) {
            AvatarRenderState submittedState = state;
            context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.lines(),
                (pose, lines) -> trailLines(pose, lines, submittedState, config, camera, partial, time));
        }
        if (config.accessoryAura) {
            int before = submittedLines;
            aura(context, matrices, state, config, camera, time);
            renderedAuraSegments = submittedLines - before;
        }
        if (!config.headAccessory) return;
        matrices.pushPose();
        try {
            Vec3 offset = state.passengerOffset == null ? Vec3.ZERO : state.passengerOffset;
            matrices.translate(state.x - camera.x + offset.x, headY - camera.y + offset.y,
                state.z - camera.z + offset.z);
            matrices.rotate(Axis.YP.rotationDegrees(-(state.bodyRot + state.yRot)));
            float scale = Math.clamp(config.accessorySize, 50, 150) / 100.0f * state.scale;
            matrices.scale(scale, scale, scale);
            context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.lines(), (pose, lines) -> {
                if (config.accessoryStyle == 1) crown(pose, lines, config.accessoryColor);
                else if (config.accessoryStyle == 2) horns(pose, lines, config.accessoryColor);
                else halo(pose, lines, config.accessoryColor);
            });
        } finally {
            matrices.popPose();
        }
    }

    private static void cape(LevelRenderContext context, AvatarRenderState state, VisualAdditionsConfig c,
                             Vec3 camera, float partial, float time) {
        PoseStack matrices = context.poseStack();
        matrices.pushPose();
        try {
            Vec3 offset = state.passengerOffset == null ? Vec3.ZERO : state.passengerOffset;
            matrices.translate(state.x - camera.x + offset.x,
                state.y - camera.y + offset.y + (state.isCrouching ? 1.27 : 1.42) * state.scale,
                state.z - camera.z + offset.z);
            matrices.rotate(Axis.YP.rotationDegrees(-state.bodyRot));
            matrices.scale(state.scale, state.scale, state.scale);
            float bend = c.capePhysics ? previousCapeBend + (capeBend - previousCapeBend) * partial : 0;
            float sway = c.capePhysics ? previousCapeSway + (capeSway - previousCapeSway) * partial : 0;
            context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, quads) -> {
                for (int row = 0; row < 16; row++) for (int column = 0; column < 12; column++) {
                    float u = (column + 0.5f) / 12, v = (row + 0.5f) / 16;
                    boolean trim = column == 0 || column == 11 || row == 15;
                    boolean motif = switch (c.capeStyle) {
                        case 1 -> Math.abs(v - (0.38f + Math.abs(u - 0.5f) * 0.65f)) < 0.065f
                            || Math.abs(v - (0.60f + Math.abs(u - 0.5f) * 0.65f)) < 0.045f;
                        case 2 -> (column * 7 + row * 13) % 37 == 0 && row > 2;
                        default -> Math.abs(Math.abs(u - 0.5f) * 1.5f + Math.abs(v - 0.5f) - 0.25f) < 0.055f;
                    };
                    int color = shade(trim || motif ? c.capeAccentColor : c.capeColor,
                        0.76f + 0.24f * (1 - v) - Math.abs(u - 0.5f) * 0.08f);
                    capeVertex(pose, quads, column / 12f, row / 16f, bend, sway, time, c.capePhysics, color);
                    capeVertex(pose, quads, (column + 1) / 12f, row / 16f, bend, sway, time, c.capePhysics, color);
                    capeVertex(pose, quads, (column + 1) / 12f, (row + 1) / 16f, bend, sway, time, c.capePhysics, color);
                    capeVertex(pose, quads, column / 12f, (row + 1) / 16f, bend, sway, time, c.capePhysics, color);
                    renderedCapeQuads++;
                }
            });
        } finally {
            matrices.popPose();
        }
    }

    private static void capeVertex(PoseStack.Pose pose, VertexConsumer quads, float u, float v,
                                   float bend, float sway, float time, boolean physics, int color) {
        double flutter = physics ? Math.sin(time * 0.13 - v * 4.0 + u * 2) * v * v * (0.018 + bend * 0.08) : 0;
        float x = (u - 0.5f) * (0.59f + v * 0.10f) - sway * v * v;
        float y = -v * (0.96f - bend * 0.20f);
        float z = (float)(-0.175 - 0.07 * v - bend * v * v + flutter);
        quads.addVertex(pose, x, y, z).setColor(color);
    }

    private static int shade(int color, float brightness) {
        int r = Math.round(((color >>> 16) & 255) * brightness);
        int g = Math.round(((color >>> 8) & 255) * brightness);
        int b = Math.round((color & 255) * brightness);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void ribbon(LevelRenderContext context, AvatarRenderState state, VisualAdditionsConfig c, Vec3 camera, float partial) {
        if (TRAIL.size() < 2) return;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, quads) -> {
            for (int i = 1; i < TRAIL.size(); i++) {
                float a = TRAIL.opacity(i - 1, c.trailLength, partial), b = TRAIL.opacity(i, c.trailLength, partial);
                int ca = alpha(c.trailColor, Math.round(a * a * 145)), cb = alpha(c.trailColor, Math.round(b * b * 145));
                float x1 = (float)(TRAIL.x(i - 1) - camera.x), y1 = (float)(TRAIL.y(i - 1) - camera.y), z1 = (float)(TRAIL.z(i - 1) - camera.z);
                boolean tip = i == TRAIL.size() - 1;
                float x2 = (float)((tip ? state.x : TRAIL.x(i)) - camera.x);
                float y2 = (float)((tip ? state.y + 0.65 : TRAIL.y(i)) - camera.y);
                float z2 = (float)((tip ? state.z : TRAIL.z(i)) - camera.z);
                quads.addVertex(pose, x1, y1 - a * 0.14f, z1).setColor(ca);
                quads.addVertex(pose, x1, y1 + a * 0.14f, z1).setColor(ca);
                quads.addVertex(pose, x2, y2 + b * 0.14f, z2).setColor(cb);
                quads.addVertex(pose, x2, y2 - b * 0.14f, z2).setColor(cb);
                renderedTrailSegments++;
            }
        });
    }

    private static void trailLines(PoseStack.Pose pose, VertexConsumer lines, AvatarRenderState state, VisualAdditionsConfig c,
                                   Vec3 camera, float partial, float time) {
        for (int i = 1; i < TRAIL.size(); i++) {
            int before = submittedLines;
            float fade = TRAIL.opacity(i, c.trailLength, partial);
            int color = alpha(c.trailColor, Math.round(fade * fade * 210));
            boolean tip = i == TRAIL.size() - 1;
            double x = (tip ? state.x : TRAIL.x(i)) - camera.x;
            double y = (tip ? state.y + 0.65 : TRAIL.y(i)) - camera.y;
            double z = (tip ? state.z : TRAIL.z(i)) - camera.z;
            if (c.trailStyle == 2) {
                double size = 0.045 * fade, drift = Math.sin(i * 2.4 + time * 0.06) * 0.20;
                segment(pose, lines, x - size, y + drift, z, x + size, y + drift, z, color);
                segment(pose, lines, x, y + drift - size, z, x, y + drift + size, z, color);
            } else {
                for (int side = -1; side <= 1; side += 2) {
                    double height = side * 0.12;
                    segment(pose, lines, TRAIL.x(i - 1) - camera.x, TRAIL.y(i - 1) - camera.y + height,
                        TRAIL.z(i - 1) - camera.z, x, y + height, z, color);
                }
            }
            renderedTrailSegments += submittedLines - before;
        }
    }

    private static void aura(LevelRenderContext context, PoseStack matrices, AvatarRenderState state,
                             VisualAdditionsConfig c, Vec3 camera, float time) {
        matrices.pushPose();
        try {
            Vec3 offset = state.passengerOffset == null ? Vec3.ZERO : state.passengerOffset;
            matrices.translate(state.x - camera.x + offset.x, state.y - camera.y + offset.y,
                state.z - camera.z + offset.z);
            double radius = Math.clamp(c.auraRadius, 40, 180) / 100.0 * state.scale;
            context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.lines(), (pose, lines) -> {
            if (c.auraStyle == 1) {
                ring(pose, lines, radius, 0.035, time * 0.008, alpha(c.auraColor, 175));
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4 + time * 0.008;
                    double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
                    double tx = -Math.sin(angle) * 0.06, tz = Math.cos(angle) * 0.06;
                    segment(pose, lines, x - tx, 0.045, z - tz, x, 0.18, z, c.auraColor);
                    segment(pose, lines, x, 0.18, z, x + tx, 0.045, z + tz, c.auraColor);
                }
            } else if (c.auraStyle == 2) {
                for (int i = 0; i < 2; i++) {
                    double phase = (time / 48.0 + i * 0.5) % 1;
                    ring(pose, lines, radius * (0.7 + phase * 0.3), 0.035, 0,
                        alpha(c.auraColor, (int)(165 * Math.sin(phase * Math.PI))));
                }
            } else {
                for (int i = 0; i < 2; i++) {
                    double phase = time * 0.025 + i * Math.PI;
                    double x = Math.cos(phase) * radius, z = Math.sin(phase) * radius;
                    double y = 0.85 + Math.sin(phase * 1.7) * 0.35;
                    double s = 0.08;
                    segment(pose, lines, x - s, y, z, x, y + s, z, c.auraColor);
                    segment(pose, lines, x, y + s, z, x + s, y, z, c.auraColor);
                    segment(pose, lines, x + s, y, z, x, y - s, z, c.auraColor);
                    segment(pose, lines, x, y - s, z, x - s, y, z, c.auraColor);
                    for (int tail = 1; tail <= 12; tail++) {
                        double a = phase - tail * 0.045, b = a + 0.045;
                        segment(pose, lines, Math.cos(a) * radius, 0.85 + Math.sin(a * 1.7) * 0.35, Math.sin(a) * radius,
                            Math.cos(b) * radius, 0.85 + Math.sin(b * 1.7) * 0.35, Math.sin(b) * radius,
                            alpha(c.auraColor, 175 - tail * 12));
                    }
                }
            }
            });
        } finally {
            matrices.popPose();
        }
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer lines, double radius, double y, double phase, int color) {
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64 + phase, b = (i + 1) * Math.PI * 2 / 64 + phase;
            segment(pose, lines, Math.cos(a) * radius, y, Math.sin(a) * radius,
                Math.cos(b) * radius, y, Math.sin(b) * radius, color);
        }
    }

    private static int alpha(int color, int opacity) { return Math.clamp(opacity, 0, 255) << 24 | color & 0xFFFFFF; }

    private static void halo(PoseStack.Pose pose, VertexConsumer vertices, int color) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48, b = (i + 1) * Math.PI * 2 / 48;
            segment(pose, vertices, Math.cos(a) * 0.34, 0.16, Math.sin(a) * 0.34,
                Math.cos(b) * 0.34, 0.16, Math.sin(b) * 0.34, color);
            segment(pose, vertices, Math.cos(a) * 0.29, 0.16, Math.sin(a) * 0.29,
                Math.cos(b) * 0.29, 0.16, Math.sin(b) * 0.29, color);
        }
    }

    private static void crown(PoseStack.Pose pose, VertexConsumer vertices, int color) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4, mid = (a + b) / 2;
            double x1 = Math.cos(a) * 0.29, z1 = Math.sin(a) * 0.29;
            double x2 = Math.cos(b) * 0.29, z2 = Math.sin(b) * 0.29;
            double tipX = Math.cos(mid) * 0.33, tipZ = Math.sin(mid) * 0.33;
            segment(pose, vertices, x1, 0, z1, x2, 0, z2, color);
            segment(pose, vertices, x1, 0.08, z1, x2, 0.08, z2, color);
            segment(pose, vertices, x1, 0.08, z1, tipX, 0.28, tipZ, color);
            segment(pose, vertices, tipX, 0.28, tipZ, x2, 0.08, z2, color);
            segment(pose, vertices, x1, 0, z1, x1, 0.08, z1, color);
        }
    }

    private static void horns(PoseStack.Pose pose, VertexConsumer vertices, int color) {
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 10; i++) {
                double a = i / 10.0, b = (i + 1) / 10.0;
                for (int edge = -1; edge <= 1; edge += 2) {
                    segment(pose, vertices, side * (0.21 + a * 0.16 + Math.sin(a * Math.PI) * 0.07),
                        a * 0.38, edge * (1 - a) * 0.065,
                        side * (0.21 + b * 0.16 + Math.sin(b * Math.PI) * 0.07),
                        b * 0.38, edge * (1 - b) * 0.065, color);
                }
            }
        }
    }

    private static void segment(PoseStack.Pose pose, VertexConsumer vertices,
                                double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.0001) return;
        submittedLines++;
        float nx = (float)(dx / length), ny = (float)(dy / length), nz = (float)(dz / length);
        vertices.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2.5f);
        vertices.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2.5f);
    }
}
