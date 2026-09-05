package dev.arcaneclient.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

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

    public static boolean prepareForTarget(MinecraftClient client, Entity target, Settings settings) {
        ClientPlayerEntity player = client.player;
        if (!settings.enabled() || player == null || target == null || client.currentScreen != null) return false;
        SpearSwitchPolicy.Settings policySettings = new SpearSwitchPolicy.Settings(
            settings.minimumTargetDistance(),
            settings.maximumTargetDistance(),
            settings.minimumDurabilityPercent()
        );
        double distance = CombatItemScoring.distanceToBox(player.getEyePos(), target.getBoundingBox());
        List<SpearSwitchPolicy.Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            int rank = CombatItemScoring.spearRank(stack);
            if (rank <= 0) continue;
            AttackRangeComponent range = stack.get(DataComponentTypes.ATTACK_RANGE);
            if (range == null) continue;
            if (!SpearSwitchPolicy.isUsefulDistance(
                distance,
                range.getEffectiveMinRange(player),
                range.getEffectiveMaxRange(player),
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

    public static void restore(MinecraftClient client) {
        restore(client, restoreSlot);
    }

    private static void restore(MinecraftClient client, int slot) {
        ClientPlayerEntity player = client.player;
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
