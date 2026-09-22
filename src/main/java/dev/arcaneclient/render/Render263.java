package dev.arcaneclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.additions.visual.VisualAdditionsConfig;
import dev.arcaneclient.mixin.RenderPipelinesAccessor;
import dev.arcaneclient.mixin.RenderTypeAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/** 26.3 extraction/submission adapter with batched, depth-layered ESP geometry. */
public final class Render263 {
    private static final RenderType THROUGH_WALL_LINES = createThroughWallLines();
    private static final RenderType THROUGH_WALL_OIT_LINES = createThroughWallOitLines();
    private static final RenderType THROUGH_WALL_FILLS = createThroughWallFills();
    private static long statWindowStarted = System.nanoTime();
    private static int statSubmissions;
    private static int statCommands;
    private static int statClustered;
    private static int shownSubmissions;
    private static int shownCommands;
    private static int shownClustered;
    private static final Map<VoxelShape, List<ShapeEdge>> SHAPE_EDGES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private Render263() { }

    private static RenderType createThroughWallLines() {
        RenderPipeline pipeline = RenderPipelinesAccessor.arcaneclient$register(
            RenderPipeline.builder(RenderPipelinesAccessor.arcaneclient$linesSnippet())
                .withLocation(ArcaneClient.id("pipeline/through_wall_lines"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        );
        RenderSetup setup = RenderSetup.builder(pipeline)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup();
        return RenderTypeAccessor.arcaneclient$create("arcaneclient_through_wall_lines", setup);
    }

    private static RenderType createThroughWallFills() {
        RenderPipeline pipeline = RenderPipelinesAccessor.arcaneclient$register(
            RenderPipeline.builder(RenderPipelinesAccessor.arcaneclient$debugFilledSnippet())
                .withLocation(ArcaneClient.id("pipeline/through_wall_fills"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .build()
        );
        OitPipelineSet oit = RenderPipelinesAccessor.arcaneclient$registerOit(
            OitPipelineSet.builder("arcaneclient_through_wall_fills",
                RenderPipeline.builder(RenderPipelinesAccessor.arcaneclient$oitDebugFilledSnippet()).withCull(false))
                .withoutDepthTest()
                .build()
        );
        RenderSetup setup = RenderSetup.builder(pipeline).setOitPipelines(oit).sortOnUpload().createRenderSetup();
        return RenderTypeAccessor.arcaneclient$create("arcaneclient_through_wall_fills", setup);
    }

    private static RenderType createThroughWallOitLines() {
        RenderPipeline pipeline = RenderPipelinesAccessor.arcaneclient$register(
            RenderPipeline.builder(RenderPipelinesAccessor.arcaneclient$linesSnippet())
                .withLocation(ArcaneClient.id("pipeline/through_wall_oit_lines"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        );
        OitPipelineSet oit = RenderPipelinesAccessor.arcaneclient$registerOit(
            OitPipelineSet.builder("arcaneclient_through_wall_lines",
                RenderPipeline.builder(RenderPipelinesAccessor.arcaneclient$oitLinesSnippet()))
                .withoutDepthTest()
                .build()
        );
        RenderSetup setup = RenderSetup.builder(pipeline).setOitPipelines(oit)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup();
        return RenderTypeAccessor.arcaneclient$create("arcaneclient_through_wall_oit_lines", setup);
    }

    public static Vec3 camera(LevelRenderContext context) {
        var state = context.levelState().cameraRenderState;
        return state == null || state.pos == null ? Vec3.ZERO : state.pos;
    }

    public static float partialTick(LevelRenderContext context) {
        return context.levelState().worldPartialTicks;
    }

    public static RenderType lines(boolean throughWalls) {
        if (throughWalls && !settings().espCompatibilityMode) return settings().espOit ? THROUGH_WALL_OIT_LINES : THROUGH_WALL_LINES;
        return RenderTypes.linesTranslucent();
    }

    public static Batch batch(LevelRenderContext context) {
        return new Batch(context, settings());
    }

    public static void outline(LevelRenderContext context, VoxelShape shape, double x, double y, double z,
                               int color, float width, boolean throughWalls) {
        batch(context).outline(shape, x, y, z, color, width, throughWalls).submit();
    }

    public static void line(LevelRenderContext context, double sx, double sy, double sz, double ex, double ey,
                            double ez, int color, float width, boolean throughWalls) {
        batch(context).line(sx, sy, sz, ex, ey, ez, color, width, throughWalls).submit();
    }

    public static void filledBox(LevelRenderContext context, double minX, double minY, double minZ, double maxX,
                                 double maxY, double maxZ, int color) {
        batch(context).filledBox(minX, minY, minZ, maxX, maxY, maxZ, color, true).submit();
    }

    public static void text(LevelRenderContext context, Font font, String value, Vec3 worldPosition, float scale,
                            int color, boolean seeThrough) {
        PoseStack poses = context.poseStack();
        if (poses == null || value == null || value.isEmpty()) return;
        Vec3 camera = camera(context);
        FormattedCharSequence text = Component.literal(value).getVisualOrderText();
        float x = -font.width(text) / 2.0f;
        poses.pushPose();
        poses.translate(worldPosition.x - camera.x, worldPosition.y - camera.y, worldPosition.z - camera.z);
        poses.mulPose(new Matrix4f().rotate(context.levelState().cameraRenderState.orientation));
        poses.scale(-scale, -scale, scale);
        OrderedSubmitNodeCollector collector = context.submitNodeCollector();
        if (seeThrough && settings().espDepthLayers && !settings().espCompatibilityMode) {
            int hiddenColor = Math.min(110, color >>> 24 == 0 ? 255 : color >>> 24) << 24 | color & 0xFFFFFF;
            collector.submitText(poses, x, 0, text, false, Font.DisplayMode.SEE_THROUGH,
                hiddenColor, 0x36000000, 0x00F000F0, 0);
            collector.submitText(poses, x, 0, text, false, Font.DisplayMode.NORMAL,
                color, 0x50000000, 0x00F000F0, 0);
        } else {
            Font.DisplayMode mode = seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
            collector.submitText(poses, x, 0, text, false, mode, color, 0x50000000, 0x00F000F0, 0);
        }
        poses.popPose();
    }

    public static String diagnostics() {
        rollStats();
        String mode = settings().espCompatibilityMode ? "COMPAT" : settings().espOit ? "OIT" : "BATCH";
        return mode + "  " + shownCommands + " cmds / " + shownSubmissions + " draws"
            + (shownClustered == 0 ? "" : " / " + shownClustered + " merged");
    }

    private static VisualAdditionsConfig settings() {
        return ArcaneClient.config() == null ? new VisualAdditionsConfig() : ArcaneClient.config().visualAdditions;
    }

    private static void record(int commands, int submissions, int clustered) {
        rollStats();
        statCommands += commands;
        statSubmissions += submissions;
        statClustered += clustered;
    }

    private static void rollStats() {
        long now = System.nanoTime();
        if (now - statWindowStarted < 1_000_000_000L) return;
        shownCommands = statCommands;
        shownSubmissions = statSubmissions;
        shownClustered = statClustered;
        statCommands = statSubmissions = statClustered = 0;
        statWindowStarted = now;
    }

    public static final class Batch {
        private final LevelRenderContext context;
        private final VisualAdditionsConfig config;
        private final Vec3 camera;
        private final List<Outline> hidden = new ArrayList<>();
        private final List<Outline> visible = new ArrayList<>();
        private final List<Line> hiddenLines = new ArrayList<>();
        private final List<Line> visibleLines = new ArrayList<>();
        private final List<Box> hiddenFills = new ArrayList<>();
        private final List<Box> visibleFills = new ArrayList<>();
        private final LongOpenHashSet farCells = new LongOpenHashSet();
        private int clustered;
        private int offered;

        private Batch(LevelRenderContext context, VisualAdditionsConfig config) {
            this.context = context;
            this.config = config;
            this.camera = camera(context);
        }

        public Batch outline(VoxelShape shape, double x, double y, double z, int color, float width, boolean throughWalls) {
            if (shape == null || shape.isEmpty() || context.poseStack() == null) return this;
            offered++;
            double distanceSquared = distanceSquared(x + 0.5, y + 0.5, z + 0.5);
            if (skipDenseFarTarget(x, y, z, distanceSquared)) return this;
            int visibleColor = distanceColor(color, distanceSquared, config.espVisibleAlpha);
            if (throughWalls && config.espDepthLayers && !config.espCompatibilityMode) {
                hidden.add(new Outline(shape, x, y, z, distanceColor(color, distanceSquared, config.espHiddenAlpha), width));
                visible.add(new Outline(shape, x, y, z, visibleColor, width + 0.15f));
            } else {
                (throughWalls ? hidden : visible).add(new Outline(shape, x, y, z, visibleColor, width));
            }
            return this;
        }

        public Batch line(double sx, double sy, double sz, double ex, double ey, double ez, int color, float width,
                          boolean throughWalls) {
            offered++;
            double distanceSquared = distanceSquared(ex, ey, ez);
            int adjusted = distanceColor(color, distanceSquared, throughWalls && config.espDepthLayers
                ? config.espHiddenAlpha : config.espVisibleAlpha);
            Line line = new Line(sx, sy, sz, ex, ey, ez, adjusted, width);
            (throughWalls ? hiddenLines : visibleLines).add(line);
            return this;
        }

        public Batch filledBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color,
                               boolean throughWalls) {
            if ((color >>> 24) == 0 || context.poseStack() == null) return this;
            offered++;
            double distanceSquared = distanceSquared((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
            if (config.espDistanceDetail && distanceSquared > (double)config.espDetailDistance * config.espDetailDistance) return this;
            int alpha = Math.min(color >>> 24, config.espFillAlpha);
            (throughWalls ? hiddenFills : visibleFills).add(
                new Box(minX, minY, minZ, maxX, maxY, maxZ, alpha << 24 | color & 0xFFFFFF));
            return this;
        }

        private double distanceSquared(double x, double y, double z) {
            double dx = x - camera.x;
            double dy = y - camera.y;
            double dz = z - camera.z;
            return dx * dx + dy * dy + dz * dz;
        }

        private boolean skipDenseFarTarget(double x, double y, double z, double distanceSquared) {
            if (config.espAdaptive && Minecraft.getInstance().getFps() < 45
                && distanceSquared > (double)config.espDetailDistance * config.espDetailDistance
                && (offered & 1) == 0) {
                clustered++;
                return true;
            }
            if (!config.espClustering
                || distanceSquared < (double)config.espClusterDistance * config.espClusterDistance) return false;
            long cx = ((long)Math.floor(x / 4.0)) & 0x1FFFFFL;
            long cy = ((long)Math.floor(y / 4.0)) & 0x3FFL;
            long cz = ((long)Math.floor(z / 4.0)) & 0x1FFFFFL;
            long key = cx | cy << 21 | cz << 31;
            if (farCells.add(key)) return false;
            clustered++;
            return true;
        }

        private int distanceColor(int color, double distanceSquared, int maximumAlpha) {
            int alpha = Math.min(color >>> 24 == 0 ? 255 : color >>> 24, maximumAlpha);
            if (config.espStableFade && distanceSquared > (double)config.espDetailDistance * config.espDetailDistance) {
                double distance = Math.sqrt(distanceSquared);
                double fade = Math.clamp(1.0 - (distance - config.espDetailDistance) / 256.0, 0.28, 1.0);
                alpha = Math.max(8, (int)Math.round(alpha * fade));
            }
            return alpha << 24 | color & 0xFFFFFF;
        }

        public void submit() {
            if (context.poseStack() == null || context.submitNodeCollector() == null) return;
            int submissions = 0;
            if (!hidden.isEmpty() || !hiddenLines.isEmpty()) {
                submitLines(config.espCompatibilityMode ? RenderTypes.linesTranslucent()
                    : config.espOit ? THROUGH_WALL_OIT_LINES : THROUGH_WALL_LINES, hidden, hiddenLines);
                submissions++;
            }
            if (!visible.isEmpty() || !visibleLines.isEmpty()) {
                submitLines(RenderTypes.linesTranslucent(), visible, visibleLines);
                submissions++;
            }
            if (!hiddenFills.isEmpty()) {
                submitBoxes(config.espOit && !config.espCompatibilityMode
                    ? THROUGH_WALL_FILLS : RenderTypes.debugFilledBox(), hiddenFills);
                submissions++;
            }
            if (!visibleFills.isEmpty()) {
                submitBoxes(RenderTypes.debugFilledBox(), visibleFills);
                submissions++;
            }
            record(offered, submissions, clustered);
        }

        private void submitLines(RenderType type, List<Outline> outlines, List<Line> lines) {
            PoseStack poses = context.poseStack();
            poses.pushPose();
            poses.translate(-camera.x, -camera.y, -camera.z);
            context.submitNodeCollector().submitCustomGeometry(poses, type, (pose, vertices) -> {
                for (Outline outline : outlines) {
                    for (ShapeEdge edge : shapeEdges(outline.shape())) {
                        TracerLines.draw(pose, vertices,
                            edge.x1() + outline.x(), edge.y1() + outline.y(), edge.z1() + outline.z(),
                            edge.x2() + outline.x(), edge.y2() + outline.y(), edge.z2() + outline.z(),
                            outline.color(), outline.width());
                    }
                }
                for (Line line : lines) TracerLines.draw(pose, vertices, line.sx(), line.sy(), line.sz(),
                    line.ex(), line.ey(), line.ez(), line.color(), line.width());
            });
            poses.popPose();
        }

        private void submitBoxes(RenderType type, List<Box> boxes) {
            PoseStack poses = context.poseStack();
            poses.pushPose();
            poses.translate(-camera.x, -camera.y, -camera.z);
            context.submitNodeCollector().submitCustomGeometry(poses, type, (pose, vertices) -> {
                for (Box box : boxes) emitBox(pose, vertices, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), box.color());
            });
            poses.popPose();
        }
    }

    private static List<ShapeEdge> shapeEdges(VoxelShape shape) {
        List<ShapeEdge> cached = SHAPE_EDGES.get(shape);
        if (cached != null) return cached;
        ArrayList<ShapeEdge> edges = new ArrayList<>();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> edges.add(new ShapeEdge(x1, y1, z1, x2, y2, z2)));
        cached = List.copyOf(edges);
        SHAPE_EDGES.put(shape, cached);
        return cached;
    }

    private static void emitBox(PoseStack.Pose pose, VertexConsumer out, double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ, int color) {
        float x0 = (float)minX, y0 = (float)minY, z0 = (float)minZ;
        float x1 = (float)maxX, y1 = (float)maxY, z1 = (float)maxZ;
        quad(pose, out, color, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        quad(pose, out, color, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
        quad(pose, out, color, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        quad(pose, out, color, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0);
        quad(pose, out, color, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        quad(pose, out, color, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int color,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz) {
        out.addVertex(pose, ax, ay, az).setColor(color);
        out.addVertex(pose, bx, by, bz).setColor(color);
        out.addVertex(pose, cx, cy, cz).setColor(color);
        out.addVertex(pose, dx, dy, dz).setColor(color);
    }

    private record Outline(VoxelShape shape, double x, double y, double z, int color, float width) { }
    private record Line(double sx, double sy, double sz, double ex, double ey, double ez, int color, float width) { }
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) { }
    private record ShapeEdge(double x1, double y1, double z1, double x2, double y2, double z2) { }
}
