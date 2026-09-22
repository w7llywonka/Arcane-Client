package dev.arcaneclient.additions.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.render.Render263;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Bounded cosmetic effects and depth-tested geometry; never changes entity interaction bounds. */
@Environment(EnvType.CLIENT)
public final class VisualAdditions {
    private static final int MAX_RINGS = 24;
    private static final int MAX_PARTICLES = 192;
    private static final int MAX_HITBOXES = 256;
    private static final ArrayDeque<Ring> RINGS = new ArrayDeque<>();
    private static final ArrayDeque<Particle> PARTICLES = new ArrayDeque<>();
    private static final List<Entity> HITBOX_TARGETS = new ArrayList<>();
    private static ClientLevel trackedWorld;
    private static Player trackedPlayer;
    private static int ticks;
    private static int hitBurstsThisTick;
    private static boolean wasGrounded;
    private static Vec3 lastFeet = Vec3.ZERO;
    private static boolean registered;

    private VisualAdditions() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        VisualAdditionsConfig c = config.visualAdditions;
        return List.of(
            GuiModule.toggle("Jump Circles", "Expanding rings at your feet when you jump or land.", () -> c.jumpCircles, v -> c.jumpCircles = v)
                .with(new GuiSetting.Swatch("Color", () -> c.jumpCircleColor, v -> c.jumpCircleColor = v))
                .with(new GuiSetting.Slider("Lifetime", () -> c.jumpCircleLifetime, v -> c.jumpCircleLifetime = v, 10, 60, " ticks"))
                .with(new GuiSetting.Slider("Radius", () -> c.jumpCircleRadius, v -> c.jumpCircleRadius = v, 50, 300, " cm"))
                .build(),
            GuiModule.toggle("Hit Particles", "Local colored particles when you attack an entity.", () -> c.hitParticles, v -> c.hitParticles = v)
                .with(new GuiSetting.Swatch("Color", () -> c.hitParticleColor, v -> c.hitParticleColor = v))
                .with(new GuiSetting.Slider("Count", () -> c.hitParticleCount, v -> c.hitParticleCount = v, 1, 24, ""))
                .with(new GuiSetting.Slider("Lifetime", () -> c.hitParticleLifetime, v -> c.hitParticleLifetime = v, 5, 40, " ticks"))
                .build(),
            GuiModule.toggle("Block Outline", "Recolors the vanilla outline of the block under your crosshair.", () -> c.blockOutline, v -> c.blockOutline = v)
                .with(new GuiSetting.Swatch("Color", () -> c.blockOutlineColor, v -> c.blockOutlineColor = v))
                .with(new GuiSetting.Slider("Width", () -> c.blockOutlineWidth, v -> c.blockOutlineWidth = v, 1, 5, " px"))
                .build(),
            GuiModule.toggle("Hitboxes", "Shows nearby living-entity bounds with normal depth testing. Visual only.", () -> c.hitboxes, v -> c.hitboxes = v)
                .with(new GuiSetting.Swatch("Color", () -> c.hitboxColor, v -> c.hitboxColor = v))
                .with(new GuiSetting.Slider("Range", () -> c.hitboxRange, v -> c.hitboxRange = v, 8, 64, "m"))
                .build(),
            GuiModule.toggle("Custom FOV", "Sets the world field of view while keeping Zoom and movement FOV effects.", () -> c.customFov, v -> c.customFov = v)
                .with(new GuiSetting.Slider("Field of view", () -> c.fov, v -> c.fov = v, 30, 140, "°"))
                .build(),
            GuiModule.toggle("Armor Trim Hider", "Hides worn armor trim rendering on this client.", () -> c.armorTrimHider, v -> c.armorTrimHider = v)
                .build(),
            GuiModule.toggle("Custom Glint", "Tints enchanted item and armor glint while keeping its animated texture.", () -> c.customGlint, v -> c.customGlint = v)
                .with(new GuiSetting.Swatch("Color", () -> c.glintColor, v -> c.glintColor = v))
                .build(),
            GuiModule.toggle("Motion Blur", "Adds timed trails to the world. Hands, HUD and menus stay clear.", () -> c.motionBlur, v -> {
                    c.motionBlur = v;
                    MotionBlur.onToggle();
                })
                .with(new GuiSetting.Slider("Strength", () -> c.motionBlurStrength, v -> c.motionBlurStrength = v, 0, 100, "%"))
                .with(new GuiSetting.Info("Status", MotionBlur::status))
                .build(),
            GuiModule.toggle("ESP Renderer", "Batched 26.3 ESP with OIT transparency, hidden/visible depth layers and adaptive detail.", () -> c.espRenderer, v -> c.espRenderer = v)
                .with(new GuiSetting.Toggle("OIT transparency", () -> c.espOit, v -> c.espOit = v))
                .with(new GuiSetting.Toggle("Multi-draw batching", () -> c.espBatching, v -> c.espBatching = v))
                .with(new GuiSetting.Toggle("Depth layers", () -> c.espDepthLayers, v -> c.espDepthLayers = v))
                .with(new GuiSetting.Toggle("Adaptive rendering", () -> c.espAdaptive, v -> c.espAdaptive = v))
                .with(new GuiSetting.Toggle("Distance detail", () -> c.espDistanceDetail, v -> c.espDistanceDetail = v))
                .with(new GuiSetting.Toggle("Stable occlusion fade", () -> c.espStableFade, v -> c.espStableFade = v))
                .with(new GuiSetting.Toggle("Dense-target clustering", () -> c.espClustering, v -> c.espClustering = v))
                .with(new GuiSetting.Toggle("Compatibility mode", () -> c.espCompatibilityMode, v -> c.espCompatibilityMode = v))
                .with(new GuiSetting.Slider("Hidden opacity", () -> c.espHiddenAlpha, v -> c.espHiddenAlpha = v, 0, 160, "/255"))
                .with(new GuiSetting.Slider("Fill opacity", () -> c.espFillAlpha, v -> c.espFillAlpha = v, 0, 160, "/255"))
                .with(new GuiSetting.Slider("Full detail range", () -> c.espDetailDistance, v -> c.espDetailDistance = v, 24, 256, "m"))
                .with(new GuiSetting.Slider("Cluster after", () -> c.espClusterDistance, v -> c.espClusterDistance = v, 48, 384, "m"))
                .with(new GuiSetting.Toggle("Renderer HUD", () -> c.rendererDiagnostics, v -> c.rendererDiagnostics = v))
                .with(new GuiSetting.Toggle("Input latency HUD", () -> c.inputLatencyHud, v -> c.inputLatencyHud = v))
                .with(new GuiSetting.Toggle("Map heading HUD", () -> c.mapHeadingHud, v -> c.mapHeadingHud = v))
                .with(new GuiSetting.Info("Renderer", Render263::diagnostics))
                .with(new GuiSetting.Info("Transparency", () -> c.espCompatibilityMode ? "LEGACY DEPTH" : c.espOit ? "OIT ACTIVE" : "ALPHA SORT"))
                .build(),
            GuiModule.value("26.3 Input", "SDL3-native input with optional buffered camera toggles and modifier chords.", () -> c.sdlInputProfile == 0 ? "NATIVE" : "BUFFERED")
                .with(new GuiSetting.Cycle("SDL profile", () -> c.sdlInputProfile == 0 ? "NATIVE" : "BUFFERED", () -> c.sdlInputProfile = 1 - c.sdlInputProfile))
                .with(new GuiSetting.Toggle("Camera bind chords", () -> c.advancedChordBinds, v -> c.advancedChordBinds = v))
                .with(new GuiSetting.Cycle("Chord modifier", () -> switch (c.chordModifier) { case 1 -> "ALT"; case 2 -> "SHIFT"; default -> "CTRL"; }, () -> c.chordModifier = (c.chordModifier + 1) % 3))
                .with(new GuiSetting.Info("Input latency", dev.arcaneclient.input.InputTelemetry::label))
                .build(),
            GuiModule.value("26.3 Content", "Small client-side helpers for Wilderness Bound content.", () -> "READY")
                .with(new GuiSetting.Toggle("Sign item preview", () -> c.signItemPreview, v -> c.signItemPreview = v))
                .with(new GuiSetting.Toggle("Cushion camera", () -> c.cushionCamera, v -> c.cushionCamera = v))
                .with(new GuiSetting.Toggle("Map heading HUD", () -> c.mapHeadingHud, v -> c.mapHeadingHud = v))
                .build(),
            GuiModule.toggle("Color Grade", "A restrained end-of-frame color wash for consistent world mood; HUD remains readable.", () -> c.colorGrade, v -> c.colorGrade = v)
                .with(new GuiSetting.Cycle("Preset", () -> colorGradeName(c.colorGradePreset), () -> c.colorGradePreset = (c.colorGradePreset + 1) % 5))
                .with(new GuiSetting.Slider("Strength", () -> c.colorGradeStrength, v -> c.colorGradeStrength = v, 0, 40, "%"))
                .build(),
            GuiModule.toggle("Custom Accessories", "Local headwear, cloth capes, motion trails and auras on your third-person or Freecam body.", () -> c.customAccessories, v -> c.customAccessories = v)
                .with(new GuiSetting.Toggle("Head accessory", () -> c.headAccessory, v -> c.headAccessory = v))
                .with(new GuiSetting.Cycle("Head style", () -> CustomAccessories.styleName(c.accessoryStyle), () -> c.accessoryStyle = (c.accessoryStyle + 1) % 3))
                .with(new GuiSetting.Swatch("Head color", () -> c.accessoryColor, v -> c.accessoryColor = v))
                .with(new GuiSetting.Slider("Head size", () -> c.accessorySize, v -> c.accessorySize = v, 50, 150, "%"))
                .with(new GuiSetting.Toggle("Cape", () -> c.accessoryCape, v -> c.accessoryCape = v))
                .with(new GuiSetting.Cycle("Cape style", () -> CustomAccessories.capeStyleName(c.capeStyle), () -> c.capeStyle = (c.capeStyle + 1) % 3))
                .with(new GuiSetting.Toggle("Cape movement physics", () -> c.capePhysics, v -> c.capePhysics = v))
                .with(new GuiSetting.Swatch("Cape fabric", () -> c.capeColor, v -> c.capeColor = v))
                .with(new GuiSetting.Swatch("Cape accent", () -> c.capeAccentColor, v -> c.capeAccentColor = v))
                .with(new GuiSetting.Toggle("Motion trail", () -> c.accessoryTrail, v -> c.accessoryTrail = v))
                .with(new GuiSetting.Cycle("Trail style", () -> CustomAccessories.trailStyleName(c.trailStyle), () -> c.trailStyle = (c.trailStyle + 1) % 3))
                .with(new GuiSetting.Slider("Trail length", () -> c.trailLength, v -> c.trailLength = v, 6, 60, " ticks"))
                .with(new GuiSetting.Swatch("Trail color", () -> c.trailColor, v -> c.trailColor = v))
                .with(new GuiSetting.Toggle("Aura", () -> c.accessoryAura, v -> c.accessoryAura = v))
                .with(new GuiSetting.Cycle("Aura style", () -> CustomAccessories.auraStyleName(c.auraStyle), () -> c.auraStyle = (c.auraStyle + 1) % 3))
                .with(new GuiSetting.Slider("Aura radius", () -> c.auraRadius, v -> c.auraRadius = v, 40, 180, " cm"))
                .with(new GuiSetting.Swatch("Aura color", () -> c.auraColor, v -> c.auraColor = v))
                .with(new GuiSetting.Info("Visibility", () -> "Only you can see these"))
                .build()
        );
    }

    public static String colorGradeName(int preset) {
        return switch (Math.clamp(preset, 0, 4)) {
            case 1 -> "WARM";
            case 2 -> "COOL";
            case 3 -> "NIGHT";
            case 4 -> "CONTRAST";
            default -> "NEUTRAL";
        };
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CustomGlint.reset();
            MotionBlur.close();
        });
        SignItemPreview.register();
        LevelRenderEvents.COLLECT_SUBMITS.register(VisualAdditions::render);
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(VisualAdditions::renderBlockOutline);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Minecraft client = Minecraft.getInstance();
            if (world == client.level && player == client.player && !player.isSpectator()) {
                spawnHitParticles(client, entity, hit == null ? entity.getBoundingBox().getCenter() : hit.getLocation());
            }
            return InteractionResult.PASS;
        });
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            reset(client);
            return;
        }
        if (trackedWorld != client.level || trackedPlayer != client.player) {
            reset(client);
            trackedWorld = client.level;
            trackedPlayer = client.player;
            wasGrounded = client.player.onGround();
            lastFeet = client.player.position();
        }
        ticks++;
        hitBurstsThisTick = 0;
        VisualAdditionsConfig c = ArcaneClient.config().visualAdditions;
        if (!c.customGlint || ArcaneVisibility.overlaysHidden()) CustomGlint.reset();
        boolean hidden = ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client);
        CustomAccessories.tick(client, c, hidden);
        if (!c.hitParticles || hidden) clearParticles();
        else PARTICLES.removeIf(particle -> !particle.isAlive());
        RINGS.removeIf(ring -> ticks - ring.born >= ring.lifetime);
        boolean grounded = client.player.onGround();
        Vec3 feet = client.player.position();
        if (!c.jumpCircles || hidden) {
            RINGS.clear();
        } else if (!client.player.isSpectator() && !client.player.getAbilities().flying
            && !client.player.isInWater() && !client.player.isInLava()
            && feet.distanceToSqr(lastFeet) < 16.0) {
            if (grounded && !wasGrounded) addRing(feet, c);
            else if (!grounded && wasGrounded && client.player.getDeltaMovement().y > 0.05) addRing(lastFeet, c);
        }
        wasGrounded = grounded;
        lastFeet = feet;
        if (!c.hitboxes || hidden) HITBOX_TARGETS.clear();
        else if (ticks % 5 == 0) refreshHitboxes(client, c);
    }

    public static void reset(Minecraft client) {
        CustomGlint.reset();
        MotionBlur.reset();
        CustomAccessories.reset();
        RINGS.clear();
        HITBOX_TARGETS.clear();
        clearParticles();
        trackedWorld = null;
        trackedPlayer = null;
        ticks = 0;
        hitBurstsThisTick = 0;
        wasGrounded = false;
        lastFeet = Vec3.ZERO;
    }

    private static void clearParticles() {
        PARTICLES.forEach(Particle::remove);
        PARTICLES.clear();
    }

    private static void addRing(Vec3 feet, VisualAdditionsConfig c) {
        while (RINGS.size() >= MAX_RINGS) RINGS.removeFirst();
        RINGS.addLast(new Ring(feet.add(0, 0.025, 0), ticks, c.jumpCircleLifetime, c.jumpCircleRadius / 100.0, c.jumpCircleColor));
    }

    private static void spawnHitParticles(Minecraft client, Entity entity, Vec3 position) {
        if (client.level == null || client.player == null || ArcaneClient.config() == null) return;
        VisualAdditionsConfig c = ArcaneClient.config().visualAdditions;
        if (!c.hitParticles || ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)
            || entity.isRemoved() || hitBurstsThisTick >= 3) return;
        hitBurstsThisTick++;
        DustParticleOptions effect = new DustParticleOptions(c.hitParticleColor & 0xFFFFFF, 0.8f);
        var random = client.level.getRandom();
        for (int i = 0; i < Math.clamp(c.hitParticleCount, 1, 24); i++) {
            while (PARTICLES.size() >= MAX_PARTICLES) PARTICLES.removeFirst().remove();
            Particle particle = client.particleEngine.createParticle(effect,
                position.x + (random.nextDouble() - 0.5) * 0.6,
                position.y + (random.nextDouble() - 0.5) * 0.6,
                position.z + (random.nextDouble() - 0.5) * 0.6,
                (random.nextDouble() - 0.5) * 0.25, 0.08 + random.nextDouble() * 0.15,
                (random.nextDouble() - 0.5) * 0.25);
            if (particle != null) {
                particle.setLifetime(Math.clamp(c.hitParticleLifetime, 5, 40));
                PARTICLES.addLast(particle);
            }
        }
    }

    private static void refreshHitboxes(Minecraft client, VisualAdditionsConfig c) {
        HITBOX_TARGETS.clear();
        Entity camera = client.getCameraEntity();
        if (camera == null) return;
        double rangeSquared = c.hitboxRange * (double)c.hitboxRange;
        for (Entity entity : client.level.getEntities(camera, camera.getBoundingBox().inflate(c.hitboxRange),
            entity -> entity instanceof LivingEntity && entity != client.player && !entity.isRemoved()
                && !entity.isInvisibleTo(client.player) && entity.distanceToSqr(camera) <= rangeSquared)) {
            HITBOX_TARGETS.add(entity);
            if (HITBOX_TARGETS.size() >= MAX_HITBOXES) break;
        }
    }

    private static boolean renderBlockOutline(LevelRenderContext context, BlockOutlineRenderState outline) {
        Minecraft client = Minecraft.getInstance();
        if (ArcaneClient.config() == null || !ArcaneClient.config().visualAdditions.blockOutline
            || !canRender(client, context) || outline == null || outline.shape().isEmpty()) return true;
        VisualAdditionsConfig c = ArcaneClient.config().visualAdditions;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack matrices = context.poseStack();
        matrices.pushPose();
        matrices.translate(outline.pos().getX() - camera.x, outline.pos().getY() - camera.y, outline.pos().getZ() - camera.z);
        context.submitNodeCollector().submitShapeOutline(matrices, outline.shape(), RenderTypes.lines(),
            c.blockOutlineColor, c.blockOutlineWidth, false);
        matrices.popPose();
        return false;
    }

    private static boolean canRender(Minecraft client, LevelRenderContext context) {
        return client.level != null && client.player != null && context.poseStack() != null && context.submitNodeCollector() != null
            && !ArcaneVisibility.overlaysHidden() && !ArcaneSettingsScreen.isOpen(client);
    }

    private static void render(LevelRenderContext context) {
        CustomAccessories.beginFrame();
        Minecraft client = Minecraft.getInstance();
        if (!canRender(client, context) || trackedWorld != client.level || ArcaneClient.config() == null) return;
        VisualAdditionsConfig c = ArcaneClient.config().visualAdditions;
        if ((!c.jumpCircles || RINGS.isEmpty()) && (!c.hitboxes || HITBOX_TARGETS.isEmpty()) && !c.customAccessories) return;
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (c.customAccessories) CustomAccessories.render(client, context, c);
        if (c.jumpCircles) context.submitNodeCollector().submitCustomGeometry(matrices, RenderTypes.lines(), (pose, lines) -> {
            for (Ring ring : RINGS) {
                double progress = Math.clamp((ticks - ring.born + partialTick) / ring.lifetime, 0.0, 1.0);
                double radius = ring.radius * (0.15 + 0.85 * progress);
                int alpha = (int)(220 * (1.0 - progress));
                int color = (alpha << 24) | (ring.color & 0xFFFFFF);
                for (int segment = 0; segment < 48; segment++) {
                    double a = segment * Math.PI * 2 / 48;
                    double b = (segment + 1) * Math.PI * 2 / 48;
                    line(pose, lines,
                        ring.position.x - camera.x + Math.cos(a) * radius, ring.position.y - camera.y, ring.position.z - camera.z + Math.sin(a) * radius,
                        ring.position.x - camera.x + Math.cos(b) * radius, ring.position.y - camera.y, ring.position.z - camera.z + Math.sin(b) * radius,
                        color, 2.0f);
                }
            }
        });
        if (c.hitboxes) for (Entity entity : HITBOX_TARGETS) {
            if (entity.isRemoved() || entity.isInvisibleTo(client.player)
                || entity.distanceToSqr(camera) > c.hitboxRange * (double)c.hitboxRange) continue;
            Vec3 lerped = entity.getPosition(partialTick);
            AABB local = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
            matrices.pushPose();
            matrices.translate(lerped.x - camera.x, lerped.y - camera.y, lerped.z - camera.z);
            context.submitNodeCollector().submitShapeOutline(matrices, Shapes.create(local), RenderTypes.lines(),
                c.hitboxColor, 1.5f, false);
            matrices.popPose();
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertices, double x1, double y1, double z1,
                             double x2, double y2, double z2, int color, float width) {
        double x = x2 - x1, y = y2 - y1, z = z2 - z1;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 0.001) return;
        float nx = (float)(x / length), ny = (float)(y / length), nz = (float)(z / length);
        vertices.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        vertices.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    private record Ring(Vec3 position, int born, int lifetime, double radius, int color) { }
}
