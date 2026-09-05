package dev.arcaneclient.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Runtime extraction helpers kept separate from the pure combat policies. */
@Environment(EnvType.CLIENT)
final class CombatItemScoring {
    private CombatItemScoring() {
    }

    static int durabilityPercent(ItemStack stack) {
        if (!stack.isDamageable()) return 100;
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
        return stack.getMaxDamage() <= 0 ? 100 : (int) Math.floor(remaining * 100.0 / stack.getMaxDamage());
    }

    static int enchantmentLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            if (enchantment.matchesKey(key)) return entry.getIntValue();
        }
        return 0;
    }

    static double attribute(
        ItemStack stack,
        RegistryEntry<EntityAttribute> attribute,
        double base,
        EquipmentSlot slot
    ) {
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return modifiers == null ? base : modifiers.applyOperations(attribute, base, slot);
    }

    static int spearRank(ItemStack stack) {
        if (stack.isOf(Items.NETHERITE_SPEAR)) return 7;
        if (stack.isOf(Items.DIAMOND_SPEAR)) return 6;
        if (stack.isOf(Items.IRON_SPEAR)) return 5;
        if (stack.isOf(Items.COPPER_SPEAR)) return 4;
        if (stack.isOf(Items.STONE_SPEAR)) return 3;
        if (stack.isOf(Items.GOLDEN_SPEAR)) return 2;
        if (stack.isOf(Items.WOODEN_SPEAR)) return 1;
        return 0;
    }

    static double distanceToBox(Vec3d point, Box box) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return point.distanceTo(new Vec3d(x, y, z));
    }
}
