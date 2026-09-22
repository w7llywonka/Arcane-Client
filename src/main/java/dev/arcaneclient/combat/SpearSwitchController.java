package dev.arcaneclient.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;

/** Selects a 1.21.11 spear only when the target is outside its zero-damage minimum reach. */
@Environment(EnvType.CLIENT)
public final class SpearSwitchController {
    public record Settings(
        boolean enabled,
        double minimumTargetDistance,
        double maximumTargetDistance,
        int minimumDurabilityPercent,
        int leaseTicks
    ) {
        public Settings {
            if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        }
    }

    private static int restoreSlot = -1;
    private static int selectedSlot = -1;

    private SpearSwitchController() {
    }

    public static boolean prepareForTarget(Minecraft client, Entity target, Settings settings) {
        LocalPlayer player = client.player;
        if (!settings.enabled() || player == null || target == null || client.gui.screen() != null) return false;
        SpearSwitchPolicy.Settings policySettings = new SpearSwitchPolicy.Settings(
            settings.minimumTargetDistance(),
            settings.maximumTargetDistance(),
            settings.minimumDurabilityPercent()
        );
        double distance = CombatItemScoring.distanceToBox(player.getEyePosition(), target.getBoundingBox());
        List<SpearSwitchPolicy.Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            int rank = CombatItemScoring.spearRank(stack);
            if (rank <= 0) continue;
            AttackRange range = stack.get(DataComponents.ATTACK_RANGE);
            if (range == null) continue;
            if (!SpearSwitchPolicy.isUsefulDistance(
                distance,
                range.effectiveMinRange(player),
                range.effectiveMaxRange(player),
                policySettings
            )) continue;
            candidates.add(new SpearSwitchPolicy.Candidate(slot, rank, CombatItemScoring.durabilityPercent(stack)));
        }
        if (candidates.isEmpty()) return false;

        int currentSlot = player.getInventory().getSelectedSlot();
        int bestSlot = SpearSwitchPolicy.choose(currentSlot, candidates, policySettings);
        if (bestSlot == currentSlot) return true;
        if (!InventoryAutomationSupport.selectHotbar(
            client,
            player,
            InventoryActionScheduler.Owner.SPEAR_SWITCH,
            bestSlot,
            settings.leaseTicks()
        )) return false;
        if (selectedSlot < 0 || currentSlot != selectedSlot) restoreSlot = currentSlot;
        selectedSlot = bestSlot;
        return true;
    }

    public static void restore(Minecraft client) {
        restore(client, restoreSlot);
    }

    private static void restore(Minecraft client, int slot) {
        LocalPlayer player = client.player;
        long tick = InventoryAutomationSupport.tick(player);
        if (player != null && slot >= 0 && slot < 9 && selectedSlot >= 0
            && player.getInventory().getSelectedSlot() == selectedSlot
            && InventoryActionScheduler.shared().isOwnedBy(
                InventoryActionScheduler.Owner.SPEAR_SWITCH,
                InventoryActionScheduler.Channel.HOTBAR_SELECTION,
                tick
            )) {
            InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.SPEAR_SWITCH,
                slot,
                1
            );
        }
        restoreSlot = -1;
        selectedSlot = -1;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.SPEAR_SWITCH);
    }
}
