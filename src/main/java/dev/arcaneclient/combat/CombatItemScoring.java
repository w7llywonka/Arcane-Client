package dev.arcaneclient.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Runtime extraction helpers kept separate from the pure combat policies. */
@Environment(EnvType.CLIENT)
final class CombatItemScoring {
    private CombatItemScoring() {
    }

    static int durabilityPercent(ItemStack stack) {
        if (!stack.isDamageableItem()) return 100;
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        return stack.getMaxDamage() <= 0 ? 100 : (int) Math.floor(remaining * 100.0 / stack.getMaxDamage());
    }

    static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        for (var entry : stack.getEnchantments().entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (enchantment.is(key)) return entry.getIntValue();
        }
        return 0;
    }

    static double attribute(
        ItemStack stack,
        Holder<Attribute> attribute,
        double base,
        EquipmentSlot slot
    ) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers == null ? base : modifiers.compute(attribute, base, slot);
    }

    static int spearRank(ItemStack stack) {
        if (stack.is(Items.NETHERITE_SPEAR)) return 7;
        if (stack.is(Items.DIAMOND_SPEAR)) return 6;
        if (stack.is(Items.IRON_SPEAR)) return 5;
        if (stack.is(Items.COPPER_SPEAR)) return 4;
        if (stack.is(Items.STONE_SPEAR)) return 3;
        if (stack.is(Items.GOLDEN_SPEAR)) return 2;
        if (stack.is(Items.WOODEN_SPEAR)) return 1;
        return 0;
    }

    static double distanceToBox(Vec3 point, AABB box) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return point.distanceTo(new Vec3(x, y, z));
    }
}
