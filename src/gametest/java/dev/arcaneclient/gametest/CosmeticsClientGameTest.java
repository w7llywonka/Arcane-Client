package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.additions.cosmetics.NametagConfig;
import dev.arcaneclient.additions.visual.CustomAccessories;
import dev.arcaneclient.additions.visual.VisualAdditions;
import dev.arcaneclient.additions.visual.VisualAdditionsConfig;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.EntityEspRenderer;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.Items;

/** Real nametag text and accessory geometry, plus images for visual inspection. */
@SuppressWarnings("UnstableApiUsage")
public final class CosmeticsClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Map<Field, Boolean> flags = new LinkedHashMap<>();
        VisualAdditionsConfig[] oldVisual = new VisualAdditionsConfig[1];
        NametagConfig[] oldNames = new NametagConfig[1];
        CameraType[] oldPerspective = new CameraType[1];
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null
                && client.levelRenderer.hasRenderedAllSections(), 5000);
            context.getInput().resizeWindow(1280, 720);
            int[] origin = context.computeOnClient(client -> {
                var c = ArcaneClient.config();
                for (Field field : ArcaneConfig.class.getFields()) {
                    if (field.getType() != boolean.class) continue;
                    try {
                        flags.put(field, field.getBoolean(c));
                        field.setBoolean(c, false);
                    } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
                }
                oldVisual[0] = c.visualAdditions;
                oldNames[0] = c.nametagAdditions;
                oldPerspective[0] = client.options.getCameraType();
                c.visualAdditions = new VisualAdditionsConfig();
                c.nametagAdditions = new NametagConfig();
                c.nametags = true;
                c.nametagAdditions.self = true;
                c.visualAdditions.customAccessories = true;
                c.visualAdditions.accessoryCape = true;
                c.visualAdditions.accessoryTrail = true;
                c.visualAdditions.accessoryAura = true;
                c.visualAdditions.trailLength = 60;
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                client.gui.setScreen(null);
                EntityEspRenderer.reset();
                VisualAdditions.reset(client);
                return new int[] { client.player.getBlockX(), client.player.getBlockY() + 4, client.player.getBlockZ() };
            });
            int x = origin[0], y = origin[1], z = origin[2];
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("fill " + (x - 12) + " " + (y - 1) + " " + (z - 12) + " " + (x + 12) + " " + (y - 1) + " " + (z + 12) + " minecraft:smooth_stone");
            world.getServer().runCommand("fill " + (x - 12) + " " + y + " " + (z - 12) + " " + (x + 12) + " " + (y + 6) + " " + (z + 12) + " minecraft:air");
            world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 8");
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
            world.getServer().runCommand("item replace entity @a armor.legs with minecraft:diamond_leggings");
            world.getServer().runCommand("item replace entity @a armor.feet with minecraft:diamond_boots");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            world.getServer().runCommand("execute at @a run summon minecraft:item ~2 ~1 ~2 {Tags:[\"arcane_cosmetic_qa\"],NoGravity:1b,PickupDelay:32767s,Item:{id:\"minecraft:diamond\",count:7}}");
            context.waitFor(client -> client.player != null && client.player.getOffhandItem().is(Items.SHIELD), 200);
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(CustomAccessories.renderedCapeQuads() > 0,
                    "The enabled cosmetic cape must submit geometry; quads=" + CustomAccessories.renderedCapeQuads());
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(EntityEspRenderer.nameLabels().stream().anyMatch(label -> label.contains(" HP")), "Self health field must render");
                require(EntityEspRenderer.nameLabels().contains("Diamond ×7"), "Dropped stack counts must preserve their default label");
                var c = ArcaneClient.config();
                c.streamerMode = true;
                require(EntityEspRenderer.nameLabels().stream().noneMatch(label -> label.contains(client.player.getName().getString())), "Streamer Mode must redact existing labels immediately");
                require(EntityEspRenderer.nameLabels().stream().anyMatch(label -> label.contains("PLAYER")), "Redacted self label must remain visible");
                c.nametagAdditions.health = false;
                c.nametagAdditions.name = false;
                require(EntityEspRenderer.nameLabels().stream().noneMatch(label -> label.contains(" HP") || label.contains("PLAYER") || label.contains("Diamond")), "Disabled text fields must not leak into existing labels");
                c.nametagAdditions.name = true;
                c.nametagAdditions.stackCount = false;
                require(EntityEspRenderer.nameLabels().contains("Diamond"), "Item count must toggle independently");
                c.nametagAdditions.health = true;
                c.nametagAdditions.stackCount = true;
            });
            for (int style = 0; style < 3; style++) {
                int currentStyle = style;
                context.runOnClient(client -> {
                    var c = ArcaneClient.config().visualAdditions;
                    c.accessoryStyle = c.capeStyle = c.trailStyle = c.auraStyle = currentStyle;
                    c.accessorySize = 100 + currentStyle * 25;
                    c.capePhysics = currentStyle != 1;
                    client.player.setYRot(currentStyle * 90);
                    client.player.setXRot(8);
                });
                context.getInput().holdKeyFor(options -> options.keyUp, 14);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    require(CustomAccessories.renderedCapeQuads() == 192, "Cape style " + currentStyle + " must submit the complete cloth mesh");
                    require(CustomAccessories.renderedTrailSegments() > 0, "Trail style " + currentStyle + " must submit visible history");
                    require(CustomAccessories.renderedAuraSegments() > 0, "Aura style " + currentStyle + " must submit geometry");
                    require(CustomAccessories.trailSampleCount() <= 64, "Trail storage must remain bounded");
                });
                context.takeScreenshot("dev21-cosmetics-style-" + style);
            }
            context.runOnClient(client -> ArcaneClient.config().cleanCapture = true);
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(EntityEspRenderer.renderedNameCount() == 0, "Clean Capture must suppress labels");
                require(CustomAccessories.renderedCapeQuads() == 0 && CustomAccessories.renderedAuraSegments() == 0
                    && CustomAccessories.renderedTrailSegments() == 0 && CustomAccessories.trailSampleCount() == 0,
                    "Clean Capture must suppress accessories and clear trail history");
                ArcaneClient.config().cleanCapture = false;
                client.options.setCameraType(CameraType.FIRST_PERSON);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(CustomAccessories.renderedCapeQuads() == 0 && CustomAccessories.renderedAuraSegments() == 0,
                    "First person must not leave floating accessories");
                FreecamController.enable(client);
            });
            context.getInput().holdKeyFor(options -> options.keyDown, 7);
            context.waitTicks(4);
            context.runOnClient(client -> {
                require(FreecamController.hasVisualBody(), "Detached camera must retain its local visual body");
                require(CustomAccessories.renderedCapeQuads() > 0 && CustomAccessories.renderedAuraSegments() > 0,
                    "Accessories must follow the submitted Freecam body");
                FreecamController.disable(client);
                EntityEspRenderer.reset();
                VisualAdditions.reset(client);
                require(CustomAccessories.trailSampleCount() == 0 && EntityEspRenderer.targetCount() == 0,
                    "World reset must discard cosmetic and nametag references");
            });
        } finally {
            context.runOnClient(client -> {
                FreecamController.disable(client);
                if (oldVisual[0] == null) return;
                var c = ArcaneClient.config();
                flags.forEach((field, value) -> {
                    try { field.setBoolean(c, value); }
                    catch (IllegalAccessException failure) { throw new AssertionError(failure); }
                });
                c.visualAdditions = oldVisual[0];
                c.nametagAdditions = oldNames[0];
                client.options.setCameraType(oldPerspective[0]);
                EntityEspRenderer.reset();
                VisualAdditions.reset(client);
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
