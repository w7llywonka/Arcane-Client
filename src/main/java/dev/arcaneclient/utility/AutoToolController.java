package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Chooses the best hotbar tool before mining and safely restores the player's slot afterward. */
@Environment(EnvType.CLIENT)
public final class AutoToolController {
    private static int restoreSlot = -1;
    private static int autoSelectedSlot = -1;

    private AutoToolController() {
    }

    public static void prepareForCrosshair(MinecraftClient client, boolean breaking) {
        ArcaneConfig config = ArcaneClient.config();
        ClientPlayerEntity player = client.player;
        if (config == null || !config.autoTool || !breaking || player == null || client.world == null || client.currentScreen != null) {
            restore(client);
            return;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            restore(client);
            return;
        }
        BlockState state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            restore(client);
            return;
        }
        selectForState(client, player, state, config.autoToolPreserveDurability);
    }

    public static void selectForState(
        MinecraftClient client,
        ClientPlayerEntity player,
        BlockState state,
        boolean preserveDurability
    ) {
        int currentSlot = player.getInventory().getSelectedSlot();
        float[] speeds = new float[9];
        boolean[] suitable = new boolean[9];
        boolean[] eligible = new boolean[9];
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            speeds[slot] = stack.getMiningSpeedMultiplier(state);
            suitable[slot] = stack.isSuitableFor(state);
            eligible[slot] = !preserveDurability
                || !stack.isDamageable()
                || stack.getMaxDamage() - stack.getDamage() > 1;
        }
        int bestSlot = AutoToolSelector.choose(currentSlot, speeds, suitable, eligible);
        if (bestSlot == currentSlot) {
            if (autoSelectedSlot >= 0 && currentSlot == autoSelectedSlot) {
                InventoryActionScheduler.shared().tryAcquire(
                    InventoryActionScheduler.Owner.AUTO_TOOL,
                    InventoryActionScheduler.Channel.HOTBAR_SELECTION,
                    InventoryAutomationSupport.tick(player),
                    2
                );
            }
            return;
        }

        if (!InventoryAutomationSupport.selectHotbar(
            client,
            player,
            InventoryActionScheduler.Owner.AUTO_TOOL,
            bestSlot,
            2
        )) {
            return;
        }
        if (autoSelectedSlot < 0 || currentSlot != autoSelectedSlot) {
            restoreSlot = currentSlot;
        }
        autoSelectedSlot = bestSlot;
    }

    public static void restore(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && restoreSlot >= 0 && restoreSlot < 9
            && player.getInventory().getSelectedSlot() == autoSelectedSlot) {
            InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.AUTO_TOOL,
                restoreSlot,
                1
            );
        }
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_TOOL);
        restoreSlot = -1;
        autoSelectedSlot = -1;
    }
}
