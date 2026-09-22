package dev.arcaneclient.additions.mining;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.mixin.additions.MiningInteractionManagerAccessor;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.render.Render263;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One mining target, read-only progress, and no world search or interaction changes. */
@Environment(EnvType.CLIENT)
public final class MiningOverlay {
    private static final int MAX_SHAPE_BOXES = 64;
    private static ClientLevel trackedWorld;
    private static Player trackedPlayer;
    private static BlockPos targetPos;
    private static BlockState targetState;
    private static VoxelShape targetShape;
    private static List<AABB> shapeBoxes = List.of();
    private static AABB shapeBounds;
    private static float observedProgress;
    private static float displayedProgress;
    private static long lastFrameNanos;
    private static boolean registered;

    private MiningOverlay() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        MiningOverlayConfig c = config.miningOverlay;
        return List.of(GuiModule.toggle("Break Progress Overlay",
                "Shows actual mining progress on the block being mined, including anchored Freecam mining.",
                () -> c.enabled, enabled -> {
                    c.enabled = enabled;
                    if (!enabled) reset(client);
                })
            .with(new GuiSetting.Swatch("Color", () -> c.color, color -> c.color = color | 0xFF000000))
            .with(new GuiSetting.Slider("Fill opacity", () -> c.fillOpacity, opacity -> c.fillOpacity = opacity, 0, 70, "%"))
            .with(new GuiSetting.Toggle("Outline", () -> c.outline, enabled -> c.outline = enabled))
            .with(new GuiSetting.Slider("Outline width", () -> c.outlineWidth, width -> c.outlineWidth = width, 1, 4, " px"))
            .with(new GuiSetting.Toggle("Percentage on block", () -> c.showPercent, enabled -> c.showPercent = enabled))
            .build());
    }

    public static void register() {
        if (registered) return;
        registered = true;
        LevelRenderEvents.COLLECT_SUBMITS.register(MiningOverlay::render);
    }

    public static void tick(Minecraft client) {
        sample(client);
    }

    public static void reset(Minecraft client) {
        trackedWorld = null;
        trackedPlayer = null;
        targetPos = null;
        targetState = null;
        targetShape = null;
        shapeBoxes = List.of();
        shapeBounds = null;
        observedProgress = 0.0f;
        displayedProgress = 0.0f;
        lastFrameNanos = 0L;
    }

    /** The interaction manager is authoritative; the detached camera crosshair is not. */
    private static boolean sample(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || config.miningOverlay == null || !config.miningOverlay.enabled
            || client.level == null || client.player == null || client.gameMode == null
            || client.gui.screen() != null || client.isPaused() || !client.options.keyAttack.isDown()
            || !client.gameMode.isDestroying()) {
            reset(client);
            return false;
        }
        MiningInteractionManagerAccessor mining = (MiningInteractionManagerAccessor)client.gameMode;
        BlockPos pos = mining.arcaneclient$miningPos();
        float measured = mining.arcaneclient$miningProgress();
        if (pos == null || !Float.isFinite(measured)) {
            reset(client);
            return false;
        }
        measured = Math.clamp(measured, 0.0f, 1.0f);
        BlockState state = client.level.getBlockState(pos);
        if (state.isAir()) {
            reset(client);
            return false;
        }
        if (trackedWorld != client.level || trackedPlayer != client.player || !pos.equals(targetPos)
            || state != targetState) {
            reset(client);
            VoxelShape shape = state.getShape(client.level, pos, CollisionContext.of(client.player));
            if (shape.isEmpty()) return false;
            List<AABB> boxes = shape.toAabbs();
            // Do not replace unusual geometry with a misleading full-block cube.
            if (boxes.isEmpty() || boxes.size() > MAX_SHAPE_BOXES) return false;
            trackedWorld = client.level;
            trackedPlayer = client.player;
            targetPos = pos.immutable();
            targetState = state;
            targetShape = shape;
            shapeBoxes = List.copyOf(boxes);
            shapeBounds = shape.bounds();
            displayedProgress = measured;
            lastFrameNanos = System.nanoTime();
        } else if (measured < observedProgress) {
            // A restart must not retain the previous attempt's higher progress.
            displayedProgress = measured;
        }
        observedProgress = measured;
        return true;
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        // Sample again so a mouse release or destroyed target cannot leave a stale frame.
        if (!sample(client) || context.poseStack() == null
            || context.levelState() == null || context.levelState().cameraRenderState == null
            || ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        MiningOverlayConfig c = ArcaneClient.config().miningOverlay;
        long now = System.nanoTime();
        double seconds = Math.clamp((now - lastFrameNanos) / 1_000_000_000.0, 0.0, 0.1);
        lastFrameNanos = now;
        // Ease only toward a value Minecraft has reported. Never predict mining completion.
        displayedProgress += (observedProgress - displayedProgress) * (float)(1.0 - Math.exp(-24.0 * seconds));
        displayedProgress = Math.clamp(displayedProgress, 0.0f, observedProgress);
        Vec3 camera = Render263.camera(context);
        int color = c.color | 0xFF000000;
        int alpha = Math.round(Math.clamp(c.fillOpacity, 0, 70) * 2.55f);
        if (alpha > 0 && displayedProgress > 0.0f) {
            double top = shapeBounds.minY + (shapeBounds.maxY - shapeBounds.minY) * displayedProgress;
            int fillColor = alpha << 24 | color & 0xFFFFFF;
            for (AABB box : shapeBoxes) {
                double clippedTop = Math.min(box.maxY, top);
                if (clippedTop > box.minY) Render263.filledBox(context,
                    targetPos.getX() + box.minX, targetPos.getY() + box.minY, targetPos.getZ() + box.minZ,
                    targetPos.getX() + box.maxX, targetPos.getY() + clippedTop, targetPos.getZ() + box.maxZ,
                    fillColor);
            }
        }
        if (c.outline) Render263.outline(context, targetShape,
            targetPos.getX(), targetPos.getY(), targetPos.getZ(), color,
            Math.clamp(c.outlineWidth, 1, 4), true);
        if (c.showPercent) drawPercent(context, client, camera, color);
    }

    private static void drawPercent(LevelRenderContext context, Minecraft client, Vec3 camera, int color) {
        String text = Math.clamp((int)(observedProgress * 100.0f), 0, 100) + "%";
        Font font = ArcaneFont.renderer(client);
        Render263.text(context, font, text,
            new Vec3(targetPos.getX() + (shapeBounds.minX + shapeBounds.maxX) / 2.0,
                targetPos.getY() + shapeBounds.maxY + 0.12,
                targetPos.getZ() + (shapeBounds.minZ + shapeBounds.maxZ) / 2.0),
            0.02f, color, true);
    }
}
