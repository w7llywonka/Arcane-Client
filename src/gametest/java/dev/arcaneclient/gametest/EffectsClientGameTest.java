package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.additions.effects.Effects;
import dev.arcaneclient.additions.effects.EffectsConfig;
import dev.arcaneclient.additions.intel.IntelAdditions;
import dev.arcaneclient.additions.intel.IntelAdditionsConfig;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.Blocks;

/** Repeated real server-side totem consumption, not a simulated GUI item event. */
@SuppressWarnings("UnstableApiUsage")
public final class EffectsClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        EffectsConfig[] original = new EffectsConfig[1];
        IntelAdditionsConfig[] originalIntel = new IntelAdditionsConfig[1];
        EffectsResourcePackFixture.Selection packSelection = null;
        boolean[] previous = new boolean[6];
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> {
                client.setScreen(null);
                var config = ArcaneClient.config();
                original[0] = config.effects;
                originalIntel[0] = config.intelAdditions;
                previous[0] = config.autoTotem;
                previous[1] = config.utilityAdditions.hoverTotem;
                previous[2] = config.utilityAdditions.inventoryTotem;
                previous[3] = config.visualAdditions.customGlint;
                previous[4] = config.viewmodel.enabled;
                previous[5] = config.enabled;
                config.effects = new EffectsConfig();
                config.intelAdditions = new IntelAdditionsConfig();
                config.intelAdditions.blockEsp = config.intelAdditions.spawnerNametags = true;
                config.intelAdditions.blockRange = config.intelAdditions.blockEntityRange = 48;
                config.intelAdditions.blocks = List.of("minecraft:spawner");
                config.intelAdditions.espSettingsVersion = 1;
                config.autoTotem = config.utilityAdditions.hoverTotem = config.utilityAdditions.inventoryTotem = false;
                config.enabled = false;
                Effects.reset(client);
                IntelAdditions.reset(client);
            });
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("gamerule minecraft:keep_inventory true");
            context.waitTicks(3);
            BlockPos spawner = context.computeOnClient(client -> client.player.blockPosition().offset(4, 1, 0));
            for (int offset = 0; offset < 2; offset++) {
                world.getServer().runCommand("setblock " + (spawner.getX() + offset * 2) + " " + spawner.getY() + " " + spawner.getZ()
                    + " minecraft:spawner{MaxNearbyEntities:0s,SpawnData:{entity:{id:\"minecraft:pig\"}}}");
            }
            // Two labels interleaved with block outlines used to invalidate the shared line consumer.
            context.waitFor(client -> spawnerLabelCount() >= 2, 200);
            context.waitTicks(5);

            // Off must exercise the original item-model animation and original totem emitter.
            for (int pop = 0; pop < 3; pop++) pop(context, world, false, 0);

            context.runOnClient(client -> {
                var config = ArcaneClient.config();
                // These existing hooks remain on while pop rendering runs.
                config.visualAdditions.customGlint = true;
                config.viewmodel.enabled = true;
            });
            // Existing first-person/glint hooks must not corrupt the original FIXED item render.
            for (int pop = 0; pop < 2; pop++) pop(context, world, false, 0);
            packSelection = EffectsResourcePackFixture.activate(context);
            if (packSelection != null) for (int pop = 0; pop < 3; pop++) pop(context, world, false, 0);
            context.runOnClient(client -> {
                var config = ArcaneClient.config();
                config.effects.totemAnimation = true;
                config.effects.totemDuration = 24;
                config.effects.totemSize = 96;
            });
            for (int style = 0; style < 3; style++) {
                int testedStyle = style;
                context.runOnClient(client -> ArcaneClient.config().effects.totemStyle = testedStyle);
                for (int pop = 0; pop < 3; pop++) pop(context, world, true, style);
            }

            // Density zero prevents actual factory allocation but never changes potion/game state.
            context.runOnClient(client -> {
                var c = ArcaneClient.config().effects;
                c.particleControl = true;
                c.totemDensity = c.explosionDensity = c.smokeDensity = c.potionDensity = c.blockDensity = c.hitDensity = 0;
                var position = client.player.position();
                require(client.particleEngine.createParticle(ParticleTypes.TOTEM_OF_UNDYING,
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled totem particles must not allocate");
                require(client.particleEngine.createParticle(ParticleTypes.EXPLOSION,
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled explosion particles must not allocate");
                require(client.particleEngine.createParticle(ParticleTypes.SMOKE,
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled smoke particles must not allocate");
                require(client.particleEngine.createParticle(ParticleTypes.CRIT,
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled hit particles must not allocate");
                require(client.particleEngine.createParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF72CFC6),
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled potion particles must not allocate");
                require(client.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled block particles must not allocate");
                require(client.particleEngine.createParticle(ParticleTypes.EXPLOSION_EMITTER,
                    position.x, position.y, position.z, 0, 0, 0) == null, "Disabled explosion emitters must not allocate");
                require(client.particleEngine.createParticle(ParticleTypes.PORTAL,
                    position.x, position.y, position.z, 0, 0, 0) != null, "Unselected vanilla particle categories must pass through");
                c.particleControl = false;
                require(client.particleEngine.createParticle(ParticleTypes.TOTEM_OF_UNDYING,
                    position.x, position.y, position.z, 0, 0, 0) != null, "Particle control OFF must preserve vanilla allocation");
                c.particleControl = true;
                c.totemDensity = 20;
                c.particlesPerTick = 32;
            });
            particleAdmission(context, spawner);
            for (int pop = 0; pop < 3; pop++) pop(context, world, true, 2);
            context.runOnClient(client -> {
                ArcaneClient.config().effects.totemDuration = 60;
                ArcaneClient.config().effects.totemStyle = 1;
            });
            pop(context, world, true, 1);
        } finally {
            context.runOnClient(client -> {
                if (original[0] == null) return;
                var config = ArcaneClient.config();
                config.effects = original[0];
                config.intelAdditions = originalIntel[0];
                config.autoTotem = previous[0];
                config.utilityAdditions.hoverTotem = previous[1];
                config.utilityAdditions.inventoryTotem = previous[2];
                config.visualAdditions.customGlint = previous[3];
                config.viewmodel.enabled = previous[4];
                config.enabled = previous[5];
                Effects.reset(client);
                IntelAdditions.reset(client);
            });
            EffectsResourcePackFixture.restore(context, packSelection);
        }
    }

    private static void pop(ClientGameTestContext context, TestSingleplayerContext world, boolean custom, int style) {
        world.getServer().runCommand("effect clear @a");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
        context.waitFor(client -> client.player != null && client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), 100);
        world.getServer().runCommand("damage @a[limit=1] 1000 minecraft:generic");
        context.waitFor(client -> client.player != null && client.player.getOffhandItem().isEmpty()
            && !client.player.isDeadOrDying() && (!custom || Effects.totemAnimationActive()), 100);
        context.runOnClient(client -> {
            ScreenEffectRenderer overlay = field(client.gameRenderer, "overlayRenderer");
            ItemStack item = field(overlay, "floatingItem");
            int timer = field(overlay, "floatingItemTimer");
            require(item != null && item.is(Items.TOTEM_OF_UNDYING) && timer > 0, "Real status packet must start the original item timer");
            require(Effects.replacesTotem(item) == custom, "Only an enabled custom animation may replace vanilla");
            require(Effects.totemAnimationActive() == custom, "Custom state must agree with the tested mode " + style);
            require(client.player.getHealth() > 0 && !client.player.isDeadOrDying(), "Cosmetic modules must preserve actual totem survival");
        });
        // Render the complete animation, including the duration longer than vanilla's 40 ticks.
        int duration = context.computeOnClient(client -> ArcaneClient.config().effects.totemDuration);
        if (custom && duration > 44) {
            context.waitTicks(44);
            context.runOnClient(client -> {
                ScreenEffectRenderer overlay = field(client.gameRenderer, "overlayRenderer");
                require(field(overlay, "floatingItem") == null, "Vanilla's original animation must expire normally");
                require(Effects.totemAnimationActive(), "A longer custom animation must outlive vanilla's 40-tick timer");
            });
            context.waitTicks(duration + 3 - 44);
        } else context.waitTicks(Math.max(44, duration + 3));
        context.runOnClient(client -> require(!Effects.totemAnimationActive(), "Animation must expire without stale pop state"));
    }

    /** Native mining and firework callers must be controlled without double thinning or null crashes. */
    private static void particleAdmission(ClientGameTestContext context, BlockPos miningBlock) {
        context.runOnClient(client -> {
            var c = ArcaneClient.config().effects;
            c.particleControl = true;
            c.blockDensity = c.explosionDensity = 0;
            c.particlesPerTick = 2048;
            Effects.reset(client);
            client.particleEngine.clearParticles();
            var position = client.player.position();
            client.level.addDestroyBlockEffect(miningBlock, Blocks.STONE.defaultBlockState());
            client.level.addBreakingBlockEffect(miningBlock, Direction.UP);
            client.particleEngine.add(new TerrainParticle(client.level, position.x, position.y, position.z,
                0, 0, 0, Blocks.STONE.defaultBlockState(), miningBlock));
            require(queuedParticles(client) == 0, "Block density zero must suppress native mining/break debris, not only factory effects");
            require(client.particleEngine.createParticle(ParticleTypes.FIREWORK,
                position.x, position.y, position.z, 0, 0, 0) != null, "Firework callers require a non-null configurable particle even when visually disabled");
            require(queuedParticles(client) == 0, "Disabled firework sparks must not enter the render/tick queue");
            c.particleControl = false;
            client.level.addDestroyBlockEffect(miningBlock, Blocks.STONE.defaultBlockState());
            require(queuedParticles(client) > 0, "Particle control OFF must preserve native break debris");
            client.particleEngine.clearParticles();
            c.particleControl = true;
            c.blockDensity = 50;
            Effects.reset(client);
            int admitted = 0;
            for (int i = 0; i < 40; i++) {
                if (client.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    position.x, position.y, position.z, 0, 0, 0) != null) admitted++;
            }
            require(admitted == 20 && queuedParticles(client) == 20, "Factory-created block particles must receive exactly one density/budget decision");
            client.particleEngine.clearParticles();
        });
        for (int density : new int[] {0, 35, 100}) {
            context.runOnClient(client -> {
                var c = ArcaneClient.config().effects;
                c.explosionDensity = density;
                c.particlesPerTick = 16;
                Effects.reset(client);
                var position = client.player.position();
                // The actual vanilla firework starter configures each returned spark without a null check.
                client.level.createFireworks(position.x, position.y + 1, position.z, 0, 0, 0,
                    List.of(new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                        IntList.of(0x44AAFF), IntList.of(0x22EEAA), true, true)));
            });
            context.waitTicks(12);
        }
        context.runOnClient(client -> {
            var c = ArcaneClient.config().effects;
            c.blockDensity = c.explosionDensity = 0;
            c.totemDensity = 20;
            c.particlesPerTick = 32;
        });
    }

    private static int queuedParticles(net.minecraft.client.Minecraft client) {
        Collection<?> queue = field(client.particleEngine, "newParticles");
        return queue.size();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object source, String name) {
        try {
            Field field = source.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T)field.get(source);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Missing actual pop-render state " + name, failure);
        }
    }

    private static int spawnerLabelCount() {
        try {
            Field field = IntelAdditions.class.getDeclaredField("renderTargets");
            field.setAccessible(true);
            List<?> targets = (List<?>)field.get(null);
            int count = 0;
            for (Object target : targets) if (field(target, "label") != null) count++;
            return count;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect actual spawner label targets", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
