package dev.arcaneclient.combat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.combat.AutoTotemSlots;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

@Environment(value=EnvType.CLIENT)
public final class AutoTotemController {
    private static final int ACK_TIMEOUT_TICKS = 20;
    private static int acknowledgementTicks;

    private AutoTotemController() {
    }

    public static void tick(MinecraftClient client) {
        if (!ArcaneClient.config().autoTotem) {
            acknowledgementTicks = 0;
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || player.isSpectator()) {
            return;
        }
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            acknowledgementTicks = 0;
            return;
        }
        if (client.currentScreen instanceof HandledScreen && !(client.currentScreen instanceof InventoryScreen) && !(client.currentScreen instanceof CreativeInventoryScreen)) {
            return;
        }
        if (acknowledgementTicks > 0) {
            --acknowledgementTicks;
            return;
        }
        if (player.currentScreenHandler != player.playerScreenHandler || !player.playerScreenHandler.getCursorStack().isEmpty()) {
            return;
        }
        int inventoryIndex = AutoTotemController.findTotem(player);
        if (inventoryIndex < 0) {
            return;
        }
        client.interactionManager.clickSlot(player.playerScreenHandler.syncId, AutoTotemSlots.inventoryMenuSlot(inventoryIndex), 40, SlotActionType.SWAP, (PlayerEntity)player);
        acknowledgementTicks = 20;
    }

    private static int findTotem(ClientPlayerEntity player) {
        for (int index = 0; index < 36; ++index) {
            ItemStack stack = player.getInventory().getStack(index);
            if (!stack.isOf(Items.TOTEM_OF_UNDYING)) continue;
            return index;
        }
        return -1;
    }
}
