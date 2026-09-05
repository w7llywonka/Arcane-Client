package dev.arcaneclient.utility;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/** Replenishes partial hotbar stacks using one cursor-safe transaction at a time. */
@Environment(EnvType.CLIENT)
public final class HotbarRefillController {
    public record Settings(
        boolean enabled,
        int threshold,
        int actionDelayTicks,
        boolean refillSelectedSlot,
        boolean requireInventoryScreen
    ) {
        public Settings {
            if (actionDelayTicks < 1) throw new IllegalArgumentException("actionDelayTicks must be positive");
        }
    }

    private static long nextActionTick;

    private HotbarRefillController() {
    }

    public static void tick(MinecraftClient client, Settings settings) {
        ClientPlayerEntity player = client.player;
        if (!settings.enabled() || player == null || client.world == null) {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.HOTBAR_REFILL);
            return;
        }
        long tick = InventoryAutomationSupport.tick(player);
        if (tick < nextActionTick || player.isUsingItem()) return;
        if (settings.requireInventoryScreen() && !(client.currentScreen instanceof InventoryScreen)) return;
        if (!InventoryAutomationSupport.canUsePlayerInventory(client, player)) return;

        List<HotbarRefillPolicy.Target> targets = new ArrayList<>();
        List<HotbarRefillPolicy.Source> sources = new ArrayList<>();
        int selected = player.getInventory().getSelectedSlot();
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack target = player.getInventory().getStack(hotbarSlot);
            if (target.isEmpty() || target.getMaxCount() <= 1) continue;
            String key = "target:" + hotbarSlot;
            targets.add(new HotbarRefillPolicy.Target(
                hotbarSlot,
                target.getCount(),
                target.getMaxCount(),
                hotbarSlot == selected,
                key
            ));
            for (int inventoryIndex = 9; inventoryIndex < 36; inventoryIndex++) {
                ItemStack source = player.getInventory().getStack(inventoryIndex);
                if (!source.isEmpty() && ItemStack.areItemsAndComponentsEqual(target, source)) {
                    sources.add(new HotbarRefillPolicy.Source(inventoryIndex, source.getCount(), key));
                }
            }
        }
        HotbarRefillPolicy.Operation operation = HotbarRefillPolicy.choose(
            targets,
            sources,
            new HotbarRefillPolicy.Settings(settings.threshold(), settings.refillSelectedSlot())
        ).orElse(null);
        if (operation == null) return;
        if (!InventoryActionScheduler.shared().tryAcquire(
            InventoryActionScheduler.Owner.HOTBAR_REFILL,
            InventoryActionScheduler.Channel.INVENTORY_CLICK,
            tick,
            Math.max(2, settings.actionDelayTicks())
        )) return;

        InventoryAutomationSupport.swapMenuSlots(
            client,
            player,
            InventoryAutomationSupport.playerMenuSlot(operation.sourceInventoryIndex()),
            InventoryAutomationSupport.playerMenuSlot(operation.targetHotbarSlot())
        );
        nextActionTick = tick + settings.actionDelayTicks();
    }

    public static void reset() {
        nextActionTick = 0L;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.HOTBAR_REFILL);
    }
}
