package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.additions.nuker.Nuker;
import dev.arcaneclient.additions.nuker.NukerConfig;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("UnstableApiUsage")
public final class NukerClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var previous = context.computeOnClient(client -> ArcaneClient.config().nuker);
        boolean autoTool = context.computeOnClient(client -> ArcaneClient.config().autoTool);
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null
                && client.levelRenderer.hasRenderedAllSections(), 5000);
            BlockPos base = context.computeOnClient(client -> {
                client.gui.setScreen(null);
                client.options.pauseOnLostFocus = false;
                var c = ArcaneClient.config();
                c.nuker = new NukerConfig(); c.autoTool = true; c.enabled = false;
                c.nuker.allBlocks = false;
                c.nuker.blocks = new ArrayList<>(List.of("minecraft:stone"));
                c.nuker.delayTicks = 10;
                require(ModuleCatalog.build(c, client).stream().flatMap(category -> category.modules().stream())
                    .anyMatch(module -> module.name().equals("Nuker")), "Nuker is registered in the real menu");
                return client.player.blockPosition();
            });
            int x = base.getX(), y = base.getY(), z = base.getZ();
            world.getServer().runCommand("fill " + (x - 7) + " " + y + " " + (z - 7) + " " + (x + 7) + " " + (y + 4) + " " + (z + 7) + " air");
            world.getServer().runCommand("fill " + (x - 7) + " " + (y - 1) + " " + (z - 7) + " " + (x + 7) + " " + (y - 1) + " " + (z + 7) + " stone");
            world.getServer().runCommand("tp @a " + (x + .5) + " " + y + " " + (z + .5) + " 0 0");
            BlockPos first = base.offset(2, 1, 0), second = base.offset(2, 0, 0), dirt = base.offset(0, 0, 2);
            BlockPos chest = base.offset(0, 0, -2), barrier = base.offset(-2, 1, 0), hidden = base.offset(-3, 1, 0), far = base.offset(6, 1, 0);
            for (BlockPos p : List.of(first, second, hidden, far)) world.getServer().runCommand(set(p, "stone"));
            world.getServer().runCommand(set(dirt, "dirt"));
            world.getServer().runCommand(set(chest, "chest"));
            // Full-height wall, so a center ray to the far stone cannot slip over/under it.
            for (int dy = -1; dy <= 2; dy++) world.getServer().runCommand(set(barrier.offset(0, dy, 0), "bedrock"));
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("item replace entity @a hotbar.0 with stick");
            world.getServer().runCommand("item replace entity @a hotbar.1 with diamond_pickaxe");
            context.waitTicks(5);
            context.runOnClient(client -> { client.player.getInventory().setSelectedSlot(0); ArcaneClient.config().nuker.enabled = true; });
            context.waitTicks(15);
            context.runOnClient(client -> require(client.level.getBlockState(first).is(Blocks.STONE), "Hold-attack default must not mine on toggle alone"));
            context.runOnClient(client -> ArcaneClient.config().nuker.holdAttack = false);
            context.waitFor(client -> Nuker.currentTarget() != null && client.player.getInventory().getSelectedSlot() == 1, 100);
            context.waitFor(client -> client.level.getBlockState(first).isAir(), 160);
            context.waitTicks(3);
            context.runOnClient(client -> require(client.level.getBlockState(second).is(Blocks.STONE), "Inter-block delay prevents immediate second break"));
            context.waitFor(client -> client.level.getBlockState(second).isAir(), 160);
            context.waitTicks(30);
            require(world.getServer().computeOnServer(server -> server.overworld().getBlockState(first).isAir()
                && server.overworld().getBlockState(second).isAir()), "Server confirms actual survival mining");
            context.runOnClient(client -> {
                require(client.level.getBlockState(dirt).is(Blocks.DIRT), "Whitelist preserves unselected dirt");
                require(client.level.getBlockState(hidden).is(Blocks.STONE), "Occluded blocks are not mined");
                require(client.level.getBlockState(far).is(Blocks.STONE), "Out-of-reach blocks are not mined");
                ArcaneClient.config().nuker.allBlocks = true;
            });
            context.waitFor(client -> client.level.getBlockState(dirt).isAir(), 200);
            context.runOnClient(client -> {
                require(client.level.getBlockState(chest).is(Blocks.CHEST), "Container protection remains on in all-block mode");
                require(client.level.getBlockState(base.below()).is(Blocks.STONE), "Floor is preserved");
                require(client.level.getBlockState(barrier).is(Blocks.BEDROCK), "Unbreakable blocks are preserved");
                ArcaneClient.config().nuker.enabled = false;
            });
            context.waitTicks(3);
            context.runOnClient(client -> require(client.player.getInventory().getSelectedSlot() == 0 && Nuker.currentTarget() == null,
                "Disable restores original hotbar slot and clears progress"));
            world.getServer().runCommand(set(first, "stone"));
            context.runOnClient(client -> { ArcaneClient.config().nuker.enabled = true; client.gui.setScreen(new ArcaneSettingsScreen(null)); });
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(client.level.getBlockState(first).is(Blocks.STONE), "Menu pauses mining");
                client.gui.setScreen(null); FreecamController.enable(client);
            });
            context.waitTicks(15);
            context.runOnClient(client -> {
                require(!Nuker.shouldHandle(client) && client.level.getBlockState(first).is(Blocks.STONE), "Freecam pauses Nuker");
                FreecamController.disable(client); FreelookController.enable(client);
            });
            context.waitTicks(15);
            context.runOnClient(client -> {
                require(!Nuker.shouldHandle(client) && client.level.getBlockState(first).is(Blocks.STONE), "Freelook pauses Nuker");
                FreelookController.disable(client); ArcaneClient.config().nuker.holdAttack = true;
            });
            context.getInput().holdKeyFor(options -> options.keyAttack, 60);
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(client.level.getBlockState(first).isAir(), "Physical held attack drives Nuker through vanilla input");
                require(Nuker.currentTarget() == null && client.player.getInventory().getSelectedSlot() == 0,
                    "Releasing attack restores the selected tool");
                ArcaneClient.config().nuker.enabled = false; Nuker.reset(client);
            });
        } finally {
            context.runOnClient(client -> {
                FreecamController.disable(client); FreelookController.disable(client); Nuker.reset(client);
                ArcaneClient.config().nuker = previous; ArcaneClient.config().autoTool = autoTool;
            });
        }
    }
    private static String set(BlockPos p, String block) { return "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + block; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
