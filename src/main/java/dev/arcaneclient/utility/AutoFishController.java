package dev.arcaneclient.utility;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bobber-state Auto Fish with bounded cast/recast timing and hotbar arbitration. */
@Environment(EnvType.CLIENT)
public final class AutoFishController {
    public record Settings(
        boolean enabled,
        boolean autoSwitch,
        int castDelayTicks,
        int recastDelayTicks,
        double biteVelocityThreshold,
        int minimumDurabilityPercent
    ) {
        public Settings {
            if (castDelayTicks < 0 || recastDelayTicks < 0) {
                throw new IllegalArgumentException("delays cannot be negative");
            }
            if (biteVelocityThreshold >= 0.0) {
                throw new IllegalArgumentException("biteVelocityThreshold must be downward/negative");
            }
            if (minimumDurabilityPercent < 0 || minimumDurabilityPercent > 100) {
                throw new IllegalArgumentException("minimumDurabilityPercent must be between 0 and 100");
            }
        }
    }

    private static final AutoFishPolicy POLICY = new AutoFishPolicy();

    private AutoFishController() {
    }

    public static void tick(Minecraft client, Settings settings) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null || client.gui.screen() != null) {
            if (player != null) POLICY.reset(player.tickCount);
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_FISH);
            return;
        }
        if (!settings.enabled() || player.isUsingItem()) {
            POLICY.reset(player.tickCount);
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_FISH);
            return;
        }
        int rodSlot = findRod(player, settings.minimumDurabilityPercent());
        boolean holdingRod = player.getMainHandItem().is(Items.FISHING_ROD);
        boolean rodAvailable = holdingRod || (settings.autoSwitch() && rodSlot >= 0);
        FishingHook bobber = player.fishing;
        AutoFishPolicy.Action action = POLICY.decide(
            player.tickCount,
            settings.enabled(),
            rodAvailable,
            bobber != null,
            bobber != null && bobber.isInWater(),
            bobber != null && bobber.getHookedIn() != null,
            bobber == null ? 0.0 : bobber.getDeltaMovement().y,
            new AutoFishPolicy.Settings(
                settings.castDelayTicks(),
                settings.recastDelayTicks(),
                settings.biteVelocityThreshold()
            )
        );
        if (action == AutoFishPolicy.Action.NONE) return;
        if (!holdingRod) {
            if (!settings.autoSwitch() || rodSlot < 0) return;
            if (!InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.AUTO_FISH,
                rodSlot,
                2
            )) return;
        } else if (!InventoryActionScheduler.shared().tryAcquire(
            InventoryActionScheduler.Owner.AUTO_FISH,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            player.tickCount,
            2
        )) return;
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
    }

    public static void reset(long tick) {
        POLICY.reset(tick);
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_FISH);
    }

    private static int findRod(LocalPlayer player, int minimumDurabilityPercent) {
        int bestSlot = -1;
        int bestDurability = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.FISHING_ROD)) continue;
            int durability = stack.isDamageableItem()
                ? (int) Math.floor((stack.getMaxDamage() - stack.getDamageValue()) * 100.0 / stack.getMaxDamage())
                : 100;
            if (durability >= minimumDurabilityPercent && durability > bestDurability) {
                bestSlot = slot;
                bestDurability = durability;
            }
        }
        return bestSlot;
    }
}
