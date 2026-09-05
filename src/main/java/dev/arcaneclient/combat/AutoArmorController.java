package dev.arcaneclient.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Equips one objectively better armor piece per configured action delay. */
@Environment(EnvType.CLIENT)
public final class AutoArmorController {
    public record Settings(
        boolean enabled,
        int actionDelayTicks,
        int minimumDurabilityPercent,
        double minimumScoreImprovement,
        boolean allowBindingCurse,
        boolean allowElytra,
        boolean pauseWhileMoving,
        boolean requireInventoryScreen
    ) {
        public Settings {
            if (actionDelayTicks < 1) throw new IllegalArgumentException("actionDelayTicks must be positive");
        }
    }

    private static long nextActionTick;

    private AutoArmorController() {
    }

    public static void tick(MinecraftClient client, Settings settings) {
        ClientPlayerEntity player = client.player;
        if (!settings.enabled() || player == null || client.world == null) {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_ARMOR);
            return;
        }
        long tick = InventoryAutomationSupport.tick(player);
        if (tick < nextActionTick || player.isUsingItem() || player.isGliding()) return;
        if (settings.requireInventoryScreen() && !(client.currentScreen instanceof InventoryScreen)) return;
        if (settings.pauseWhileMoving() && player.getVelocity().horizontalLengthSquared() > 1.0e-4) return;
        if (!InventoryAutomationSupport.canUsePlayerInventory(client, player)) return;

        List<AutoArmorPolicy.Candidate> inventory = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            AutoArmorPolicy.Candidate candidate = candidate(player.getInventory().getStack(index), index);
            if (candidate != null) inventory.add(candidate);
        }
        List<AutoArmorPolicy.Candidate> equipped = new ArrayList<>();
        for (EquipmentSlot slot : armorSlots()) {
            AutoArmorPolicy.Candidate candidate = candidate(player.getEquippedStack(slot), -1);
            if (candidate != null) equipped.add(candidate);
        }

        AutoArmorPolicy.Settings policySettings = new AutoArmorPolicy.Settings(
            settings.minimumDurabilityPercent(),
            settings.minimumScoreImprovement(),
            settings.allowBindingCurse(),
            settings.allowElytra()
        );
        AutoArmorPolicy.Candidate upgrade = AutoArmorPolicy.chooseUpgrade(inventory, equipped, policySettings).orElse(null);
        if (upgrade == null) return;
        EquipmentSlot equipmentSlot = equipmentSlot(upgrade.slot());
        if (!InventoryActionScheduler.shared().tryAcquire(
            InventoryActionScheduler.Owner.AUTO_ARMOR,
            InventoryActionScheduler.Channel.INVENTORY_CLICK,
            tick,
            Math.max(2, settings.actionDelayTicks())
        )) return;

        InventoryAutomationSupport.swapMenuSlots(
            client,
            player,
            InventoryAutomationSupport.playerMenuSlot(upgrade.inventoryIndex()),
            InventoryAutomationSupport.armorMenuSlot(equipmentSlot)
        );
        nextActionTick = tick + settings.actionDelayTicks();
    }

    public static void reset() {
        nextActionTick = 0L;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_ARMOR);
    }

    private static AutoArmorPolicy.Candidate candidate(ItemStack stack, int inventoryIndex) {
        if (stack.isEmpty()) return null;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null || !isArmorSlot(equippable.slot())) return null;
        EquipmentSlot slot = equippable.slot();
        double armor = CombatItemScoring.attribute(stack, EntityAttributes.ARMOR, 0.0, slot);
        double toughness = CombatItemScoring.attribute(stack, EntityAttributes.ARMOR_TOUGHNESS, 0.0, slot);
        double knockback = CombatItemScoring.attribute(stack, EntityAttributes.KNOCKBACK_RESISTANCE, 0.0, slot);
        int protection = CombatItemScoring.enchantmentLevel(stack, Enchantments.PROTECTION);
        int specialist = CombatItemScoring.enchantmentLevel(stack, Enchantments.BLAST_PROTECTION)
            + CombatItemScoring.enchantmentLevel(stack, Enchantments.FIRE_PROTECTION)
            + CombatItemScoring.enchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION)
            + CombatItemScoring.enchantmentLevel(stack, Enchantments.FEATHER_FALLING);
        boolean binding = CombatItemScoring.enchantmentLevel(stack, Enchantments.BINDING_CURSE) > 0;
        return new AutoArmorPolicy.Candidate(
            inventoryIndex,
            policySlot(slot),
            armor,
            toughness,
            knockback,
            protection,
            specialist,
            CombatItemScoring.durabilityPercent(stack),
            binding,
            stack.isOf(Items.ELYTRA)
        );
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static EquipmentSlot[] armorSlots() {
        return new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    }

    private static AutoArmorPolicy.Slot policySlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> AutoArmorPolicy.Slot.HEAD;
            case CHEST -> AutoArmorPolicy.Slot.CHEST;
            case LEGS -> AutoArmorPolicy.Slot.LEGS;
            case FEET -> AutoArmorPolicy.Slot.FEET;
            default -> throw new IllegalArgumentException("not an armor slot: " + slot);
        };
    }

    private static EquipmentSlot equipmentSlot(AutoArmorPolicy.Slot slot) {
        return switch (slot) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }
}
