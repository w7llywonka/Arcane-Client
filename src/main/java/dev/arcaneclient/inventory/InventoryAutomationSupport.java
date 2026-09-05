package dev.arcaneclient.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

/** Shared mapped-slot and packet helpers for player inventory automation. */
@Environment(EnvType.CLIENT)
public final class InventoryAutomationSupport {
    private InventoryAutomationSupport() {
    }

    public static long tick(ClientPlayerEntity player) {
        return player == null ? 0L : player.age;
    }

    public static boolean canUsePlayerInventory(MinecraftClient client, ClientPlayerEntity player) {
        if (client.interactionManager == null || player == null || player.isSpectator()) return false;
        if (client.currentScreen instanceof HandledScreen
            && !(client.currentScreen instanceof InventoryScreen)
            && !(client.currentScreen instanceof CreativeInventoryScreen)) {
            return false;
        }
        return player.currentScreenHandler == player.playerScreenHandler
            && player.playerScreenHandler.getCursorStack().isEmpty();
    }

    public static int playerMenuSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= 36) {
            throw new IllegalArgumentException("inventory index must be between 0 and 35");
        }
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }

    public static int armorMenuSlot(net.minecraft.entity.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> throw new IllegalArgumentException("not a player armor slot: " + slot);
        };
    }

    public static boolean selectHotbar(
        MinecraftClient client,
        ClientPlayerEntity player,
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
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        }
        return true;
    }

    /** Swaps two player-menu stacks without leaving an item on the cursor. */
    public static void swapMenuSlots(MinecraftClient client, ClientPlayerEntity player, int first, int second) {
        int syncId = player.playerScreenHandler.syncId;
        client.interactionManager.clickSlot(syncId, first, 0, SlotActionType.PICKUP, (PlayerEntity) player);
        client.interactionManager.clickSlot(syncId, second, 0, SlotActionType.PICKUP, (PlayerEntity) player);
        client.interactionManager.clickSlot(syncId, first, 0, SlotActionType.PICKUP, (PlayerEntity) player);
    }
}
