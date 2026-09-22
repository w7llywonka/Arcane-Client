package dev.arcaneclient.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

/** Shared mapped-slot and packet helpers for player inventory automation. */
@Environment(EnvType.CLIENT)
public final class InventoryAutomationSupport {
    private InventoryAutomationSupport() {
    }

    public static long tick(LocalPlayer player) {
        return player == null ? 0L : player.tickCount;
    }

    public static boolean canUsePlayerInventory(Minecraft client, LocalPlayer player) {
        if (client.gameMode == null || player == null || player.isSpectator()) return false;
        if (client.gui.screen() instanceof AbstractContainerScreen
            && !(client.gui.screen() instanceof InventoryScreen)
            && !(client.gui.screen() instanceof CreativeModeInventoryScreen)) {
            return false;
        }
        return player.containerMenu == player.inventoryMenu
            && player.inventoryMenu.getCarried().isEmpty();
    }

    public static int playerMenuSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= 36) {
            throw new IllegalArgumentException("inventory index must be between 0 and 35");
        }
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }

    public static int armorMenuSlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> throw new IllegalArgumentException("not a player armor slot: " + slot);
        };
    }

    public static boolean selectHotbar(
        Minecraft client,
        LocalPlayer player,
        InventoryActionScheduler.Owner owner,
        int slot,
        int holdTicks
    ) {
        if (slot < 0 || slot >= 9) return false;
        long tick = tick(player);
        if (!InventoryActionScheduler.shared().tryAcquire(
            owner,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            tick,
            holdTicks
        )) return false;

        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            }
        }
        return true;
    }

    /** Swaps two player-menu stacks without leaving an item on the cursor. */
    public static void swapMenuSlots(Minecraft client, LocalPlayer player, int first, int second) {
        int syncId = player.inventoryMenu.containerId;
        client.gameMode.handleContainerInput(syncId, first, 0, ContainerInput.PICKUP, (Player) player);
        client.gameMode.handleContainerInput(syncId, second, 0, ContainerInput.PICKUP, (Player) player);
        client.gameMode.handleContainerInput(syncId, first, 0, ContainerInput.PICKUP, (Player) player);
    }
}
