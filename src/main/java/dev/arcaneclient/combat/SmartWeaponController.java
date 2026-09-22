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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** Selects the strongest eligible hotbar weapon for a concrete target. */
@Environment(EnvType.CLIENT)
public final class SmartWeaponController {
    public record Settings(
        boolean enabled,
        SmartWeaponPolicy.Mode mode,
        int minimumDurabilityPercent,
        boolean preferShieldBreaker,
        int leaseTicks
    ) {
        public Settings {
            if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        }
    }

    private static int restoreSlot = -1;
    private static int selectedSlot = -1;

    private SmartWeaponController() {
    }

    public static boolean prepareForTarget(Minecraft client, Entity target, Settings settings) {
        LocalPlayer player = client.player;
        if (!settings.enabled() || player == null || target == null || client.gui.screen() != null) return false;
        int currentSlot = player.getInventory().getSelectedSlot();
        List<SmartWeaponPolicy.Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.has(DataComponents.WEAPON)) continue;
            double attackDamage = CombatItemScoring.attribute(
                stack,
                Attributes.ATTACK_DAMAGE,
                1.0,
                EquipmentSlot.MAINHAND
            );
            double attackSpeed = CombatItemScoring.attribute(
                stack,
                Attributes.ATTACK_SPEED,
                4.0,
                EquipmentSlot.MAINHAND
            );
            double enchantmentDamage = CombatItemScoring.enchantmentLevel(stack, Enchantments.SHARPNESS) * 0.75
                + CombatItemScoring.enchantmentLevel(stack, Enchantments.SMITE) * 0.2
                + CombatItemScoring.enchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) * 0.2
                + CombatItemScoring.enchantmentLevel(stack, Enchantments.IMPALING) * 0.2;
            double armorPenetration = CombatItemScoring.enchantmentLevel(stack, Enchantments.BREACH) * 0.15;
            candidates.add(new SmartWeaponPolicy.Candidate(
                slot,
                attackDamage,
                attackSpeed,
                enchantmentDamage,
                armorPenetration,
                stack.is(ItemTags.AXES),
                CombatItemScoring.durabilityPercent(stack),
                true
            ));
        }

        double armor = target instanceof LivingEntity living ? living.getArmorValue() : 0.0;
        boolean blocking = target instanceof LivingEntity living && living.isBlocking();
        int bestSlot = SmartWeaponPolicy.choose(
            currentSlot,
            candidates,
            new SmartWeaponPolicy.Target(armor, blocking),
            new SmartWeaponPolicy.Settings(settings.mode(), settings.minimumDurabilityPercent(), settings.preferShieldBreaker())
        );
        if (bestSlot == currentSlot) return true;
        if (!InventoryAutomationSupport.selectHotbar(
            client,
            player,
            InventoryActionScheduler.Owner.SMART_WEAPON,
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
                InventoryActionScheduler.Owner.SMART_WEAPON,
                InventoryActionScheduler.Channel.HOTBAR_SELECTION,
                tick
            )) {
            InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.SMART_WEAPON,
                restoreSlot,
                1
            );
        }
        restoreSlot = -1;
        selectedSlot = -1;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.SMART_WEAPON);
    }
}
