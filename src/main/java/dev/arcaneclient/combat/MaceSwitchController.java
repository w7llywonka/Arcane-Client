package dev.arcaneclient.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;

/** Selects a mace for a real falling smash with a target below the player. */
@Environment(EnvType.CLIENT)
public final class MaceSwitchController {
    public record Settings(
        boolean enabled,
        double minimumFallDistance,
        double maximumVerticalVelocity,
        double minimumTargetDrop,
        int minimumDurabilityPercent,
        int leaseTicks
    ) {
        public Settings {
            if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        }
    }

    private static int restoreSlot = -1;
    private static int selectedSlot = -1;

    private MaceSwitchController() {
    }

    public static boolean prepareForTarget(Minecraft client, Entity target, Settings settings) {
        LocalPlayer player = client.player;
        if (!settings.enabled() || player == null || target == null || client.gui.screen() != null) return false;
        if (!MaceItem.canSmashAttack(player)) return false;
        MaceSwitchPolicy.Settings policySettings = new MaceSwitchPolicy.Settings(
            settings.minimumFallDistance(),
            settings.maximumVerticalVelocity(),
            settings.minimumTargetDrop(),
            settings.minimumDurabilityPercent()
        );
        double targetDrop = player.getY() - target.getBoundingBox().maxY;
        MaceSwitchPolicy.Context context = new MaceSwitchPolicy.Context(
            player.fallDistance,
            player.getDeltaMovement().y,
            targetDrop,
            player.isFallFlying(),
            player.isInWater(),
            player.onClimbable()
        );
        if (!MaceSwitchPolicy.shouldSwitch(context, policySettings)) return false;

        List<MaceSwitchPolicy.Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.MACE)) {
                candidates.add(new MaceSwitchPolicy.Candidate(slot, CombatItemScoring.durabilityPercent(stack)));
            }
        }
        int currentSlot = player.getInventory().getSelectedSlot();
        int bestSlot = MaceSwitchPolicy.choose(currentSlot, candidates, policySettings);
        if (bestSlot == currentSlot) return !candidates.isEmpty();
        if (!InventoryAutomationSupport.selectHotbar(
            client,
            player,
            InventoryActionScheduler.Owner.MACE_SWITCH,
            bestSlot,
            settings.leaseTicks()
        )) return false;
        if (selectedSlot < 0 || currentSlot != selectedSlot) restoreSlot = currentSlot;
        selectedSlot = bestSlot;
        return true;
    }

    public static void restore(Minecraft client) {
        LocalPlayer player = client.player;
        long tick = InventoryAutomationSupport.tick(player);
        if (player != null && restoreSlot >= 0 && restoreSlot < 9 && selectedSlot >= 0
            && player.getInventory().getSelectedSlot() == selectedSlot
            && InventoryActionScheduler.shared().isOwnedBy(
                InventoryActionScheduler.Owner.MACE_SWITCH,
                InventoryActionScheduler.Channel.HOTBAR_SELECTION,
                tick
            )) {
            InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.MACE_SWITCH,
                restoreSlot,
                1
            );
        }
        restoreSlot = -1;
        selectedSlot = -1;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.MACE_SWITCH);
    }
}
