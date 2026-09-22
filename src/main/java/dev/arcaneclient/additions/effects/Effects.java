package dev.arcaneclient.additions.effects;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native GUI animation and pre-allocation particle admission; no gameplay or raw GL hooks. */
@Environment(EnvType.CLIENT)
public final class Effects {
    private static final Identifier TOTEM_TEXTURE = Identifier.withDefaultNamespace("textures/item/totem_of_undying.png");
    private static final String[] STYLES = {"Spin", "Slide", "Fade"};
    private static final ParticleControlPolicy.Budget PARTICLES = new ParticleControlPolicy.Budget();
    private static ClientLevel trackedWorld;
    private static Player trackedPlayer;
    private static int totemAge;
    private static int totemDuration;
    private static boolean replacedTotem;
    private static boolean registered;

    private Effects() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        EffectsConfig c = config.effects;
        return List.of(
            GuiModule.toggle("Totem Animation", "A clean screen-space totem icon using your resource pack texture. Changes only the pop visual.",
                    () -> c.totemAnimation, enabled -> {
                        c.totemAnimation = enabled;
                        if (!enabled) clearTotem();
                    })
                .with(new GuiSetting.Cycle("Style", () -> STYLES[Math.clamp(c.totemStyle, 0, 2)], () -> c.totemStyle = (c.totemStyle + 1) % 3))
                .with(new GuiSetting.Slider("Duration", () -> c.totemDuration, value -> c.totemDuration = value, 6, 80, " ticks"))
                .with(new GuiSetting.Slider("Size", () -> c.totemSize, value -> c.totemSize = value, 32, 220, " px"))
                .with(new GuiSetting.Slider("Horizontal offset", () -> c.totemOffsetX, value -> c.totemOffsetX = value, -300, 300, " px"))
                .with(new GuiSetting.Slider("Vertical offset", () -> c.totemOffsetY, value -> c.totemOffsetY = value, -200, 200, " px"))
                .build(),
            GuiModule.toggle("Particle Control", "Reduces selected vanilla particle categories before rendering. Zero density disables a category; other particles pass through.",
                    () -> c.particleControl, enabled -> {
                        c.particleControl = enabled;
                        PARTICLES.reset();
                    })
                .with(new GuiSetting.Slider("Totem density", () -> c.totemDensity, value -> c.totemDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Slider("Explosion density", () -> c.explosionDensity, value -> c.explosionDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Slider("Smoke density", () -> c.smokeDensity, value -> c.smokeDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Slider("Potion density", () -> c.potionDensity, value -> c.potionDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Slider("Block density", () -> c.blockDensity, value -> c.blockDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Slider("Hit density", () -> c.hitDensity, value -> c.hitDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Slider("Poplar leaves", () -> c.poplarDensity, value -> c.poplarDensity = value, 0, 100, "%"))
                .with(new GuiSetting.Toggle("Adaptive budget", () -> c.adaptiveParticleBudget, value -> c.adaptiveParticleBudget = value))
                .with(new GuiSetting.Slider("Target frame rate", () -> c.particleTargetFps, value -> c.particleTargetFps = value, 30, 240, " FPS"))
                .with(new GuiSetting.Slider("Selected-particle budget", () -> c.particlesPerTick, value -> c.particlesPerTick = value, 16, 2048, "/tick"))
                .build());
    }

    public static void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ArcaneClient.id("totem_animation"), Effects::renderTotem);
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null || trackedWorld != client.level || trackedPlayer != client.player) {
            reset(client);
            trackedWorld = client.level;
            trackedPlayer = client.player;
        }
        EffectsConfig c = settings();
        if (c == null || !c.totemAnimation) clearTotem();
        else if (totemAge < totemDuration && !client.isPaused()) totemAge++;
        if (c == null || !c.particleControl) PARTICLES.reset();
    }

    public static void reset(Minecraft client) {
        clearTotem();
        PARTICLES.reset();
        trackedWorld = null;
        trackedPlayer = null;
    }

    public static void startTotem(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        EffectsConfig c = settings();
        clearTotem();
        if (c == null || !c.totemAnimation || stack == null || !stack.is(Items.TOTEM_OF_UNDYING)
            || client.level == null || client.player == null) return;
        if (trackedWorld != client.level || trackedPlayer != client.player) PARTICLES.reset();
        trackedWorld = client.level;
        trackedPlayer = client.player;
        totemAge = 0;
        totemDuration = Math.clamp(c.totemDuration, 6, 80);
        replacedTotem = true;
    }

    public static void clearTotem() {
        totemAge = 0;
        totemDuration = 0;
        replacedTotem = false;
    }

    public static boolean replacesTotem(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        EffectsConfig c = settings();
        return replacedTotem && c != null && c.totemAnimation && stack != null && stack.is(Items.TOTEM_OF_UNDYING)
            && trackedWorld == client.level && trackedPlayer == client.player && client.level != null;
    }

    public static boolean totemAnimationActive() {
        return replacedTotem && totemAge < totemDuration;
    }

    public static boolean allowParticle(ParticleOptions effect, ClientLevel world) {
        EffectsConfig c = settings();
        if (c == null || !c.particleControl || world == null) return true;
        Minecraft client = Minecraft.getInstance();
        if (world != client.level) return true;
        if (trackedWorld != world || trackedPlayer != client.player) {
            reset(client);
            trackedWorld = world;
            trackedPlayer = client.player;
        }
        String id = particleId(effect);
        // Vanilla fireworks immediately configure the returned spark without a null check.
        // Their instances are instead rejected before enqueueing, preserving that contract.
        if ("minecraft:firework".equals(id)) return true;
        ParticleControlPolicy.Category category = ParticleControlPolicy.category(id);
        if (category != null && ParticleControlPolicy.emitterCarrier(id)) return Math.clamp(c.density(category), 0, 100) > 0;
        return category == null || PARTICLES.allow(category, c.density(category), particleBudget(c), world.getGameTime());
    }

    /** Covers native mining debris and fireworks without nulling a caller-owned particle. */
    public static boolean allowParticleInstance(Particle particle, ClientLevel world, boolean factoryAdmitted) {
        EffectsConfig c = settings();
        Minecraft client = Minecraft.getInstance();
        if (c == null || !c.particleControl || world == null || world != client.level || particle == null) return true;
        ParticleControlPolicy.Category category;
        if (particle instanceof FireworkVisualParticle) category = ParticleControlPolicy.Category.EXPLOSION;
        else if (particle instanceof TerrainParticle && !factoryAdmitted) category = ParticleControlPolicy.Category.BLOCK;
        else return true;
        if (trackedWorld != world || trackedPlayer != client.player) {
            reset(client);
            trackedWorld = world;
            trackedPlayer = client.player;
        }
        return PARTICLES.allow(category, c.density(category), particleBudget(c), world.getGameTime());
    }

    /** A disabled emitter category can be skipped without allocating its tick emitter. */
    public static boolean allowEmitter(ParticleOptions effect, ClientLevel world) {
        EffectsConfig c = settings();
        if (c == null || !c.particleControl || world == null || world != Minecraft.getInstance().level) return true;
        ParticleControlPolicy.Category category = category(effect);
        return category == null || Math.clamp(c.density(category), 0, 100) > 0;
    }

    private static ParticleControlPolicy.Category category(ParticleOptions effect) {
        return ParticleControlPolicy.category(particleId(effect));
    }

    private static String particleId(ParticleOptions effect) {
        if (effect == null) return null;
        Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(effect.getType());
        return id == null ? null : id.toString();
    }

    private static EffectsConfig settings() {
        ArcaneConfig config = ArcaneClient.config();
        return config == null ? null : config.effects;
    }

    private static int particleBudget(EffectsConfig c) {
        if (!c.adaptiveParticleBudget) return c.particlesPerTick;
        Minecraft client = Minecraft.getInstance();
        int fps = Math.max(1, client.getFps());
        double scale = Math.clamp(fps / (double)Math.max(30, c.particleTargetFps), 0.25, 1.0);
        return Math.max(16, (int)Math.round(c.particlesPerTick * scale));
    }

    private static void renderTotem(GuiGraphicsExtractor draw, DeltaTracker counter) {
        Minecraft client = Minecraft.getInstance();
        EffectsConfig c = settings();
        if (c == null || !c.totemAnimation || !totemAnimationActive() || trackedWorld != client.level
            || trackedPlayer != client.player || client.level == null || client.player == null
            || client.gui.hud.isHidden() || ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        float partialTick = client.isPaused() ? 0 : counter.getGameTimeDeltaPartialTick(false);
        TotemAnimationMath.Frame frame = TotemAnimationMath.frame(c.totemStyle, totemAge + partialTick, totemDuration);
        int alpha = Math.round(frame.opacity() * 255.0f);
        if (alpha <= 0) return;
        int size = Math.min(Math.clamp(c.totemSize, 32, 220), Math.min(draw.guiWidth(), draw.guiHeight()) - 8);
        if (size < 16) return;
        float centerX = Math.clamp(draw.guiWidth() / 2.0f + c.totemOffsetX, size / 2.0f, draw.guiWidth() - size / 2.0f);
        float centerY = Math.clamp(draw.guiHeight() / 2.0f + c.totemOffsetY, size / 2.0f, draw.guiHeight() - size / 2.0f);
        var matrices = draw.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(centerX, centerY + frame.slideY());
            matrices.rotate(frame.rotation());
            matrices.scale(frame.scale(), frame.scale());
            draw.blit(RenderPipelines.GUI_TEXTURED, TOTEM_TEXTURE, -size / 2, -size / 2,
                0.0f, 0.0f, size, size, 16, 16, 16, 16, alpha << 24 | 0xFFFFFF);
        } finally {
            matrices.popMatrix();
        }
    }
}
