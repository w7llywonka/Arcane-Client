package dev.arcaneclient.combat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.combat.AutoTotemSlots;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(value=EnvType.CLIENT)
public final class AutoTotemController {
    private static final int ACK_TIMEOUT_TICKS = 20;
    private static int acknowledgementTicks;

    private AutoTotemController() {
    }

    public static void tick(Minecraft client) {
        if (!ArcaneClient.config().autoTotem) {
            acknowledgementTicks = 0;
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_TOTEM);
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || player.isSpectator()) {
            acknowledgementTicks = 0;
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_TOTEM);
            return;
        }
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            acknowledgementTicks = 0;
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_TOTEM);
            return;
        }
        if (client.gui.screen() instanceof AbstractContainerScreen && !(client.gui.screen() instanceof InventoryScreen) && !(client.gui.screen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        if (acknowledgementTicks > 0) {
            --acknowledgementTicks;
            return;
        }
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        int inventoryIndex = AutoTotemController.findTotem(player);
        if (inventoryIndex < 0) {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_TOTEM);
            return;
        }
        if (!InventoryActionScheduler.shared().tryAcquire(
            InventoryActionScheduler.Owner.AUTO_TOTEM,
            InventoryActionScheduler.Channel.INVENTORY_CLICK,
            InventoryAutomationSupport.tick(player),
            ACK_TIMEOUT_TICKS
        )) {
            return;
        }
        client.gameMode.handleContainerInput(player.inventoryMenu.containerId, AutoTotemSlots.inventoryMenuSlot(inventoryIndex), 40, ContainerInput.SWAP, (Player)player);
        acknowledgementTicks = ACK_TIMEOUT_TICKS;
    }

    public static void reset() {
        acknowledgementTicks = 0;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_TOTEM);
    }

    private static int findTotem(LocalPlayer player) {
        for (int index = 0; index < 36; ++index) {
            ItemStack stack = player.getInventory().getItem(index);
            if (!stack.is(Items.TOTEM_OF_UNDYING)) continue;
            return index;
        }
        return -1;
    }
}
