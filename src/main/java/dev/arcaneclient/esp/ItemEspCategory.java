package dev.arcaneclient.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@Environment(value=EnvType.CLIENT)
public enum ItemEspCategory {
    TOTEMS("Totems", -218147499),
    CRYSTALS("End crystals", -218147363),
    ELYTRA("Elytra", -229253633),
    SHULKERS("Shulkers", -223713537),
    GAPPLES("Golden apples", -218117069),
    VALUABLES("Valuables", -229245014);

    private final String label;
    private final int defaultColor;

    private ItemEspCategory(String label, int defaultColor) {
        this.label = label;
        this.defaultColor = defaultColor;
    }

    public String label() {
        return this.label;
    }

    public int defaultColor() {
        return this.defaultColor;
    }

    public boolean matches(ItemStack stack) {
        return switch (this.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> stack.isOf(Items.TOTEM_OF_UNDYING);
            case 1 -> stack.isOf(Items.END_CRYSTAL);
            case 2 -> stack.isOf(Items.ELYTRA);
            case 3 -> {
                BlockItem blockItem;
                Item var3_2 = stack.getItem();
                if (var3_2 instanceof BlockItem && (blockItem = (BlockItem)var3_2).getBlock() instanceof ShulkerBoxBlock) {
                    yield true;
                }
                yield false;
            }
            case 4 -> {
                if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                    yield true;
                }
                yield false;
            }
            case 5 -> stack.isOf(Items.DIAMOND) || stack.isOf(Items.DIAMOND_BLOCK) || stack.isOf(Items.NETHERITE_INGOT) || stack.isOf(Items.NETHERITE_BLOCK) || stack.isOf(Items.NETHERITE_SCRAP) || stack.isOf(Items.ANCIENT_DEBRIS);
        };
    }

    public static ItemEspCategory match(ItemStack stack) {
        for (ItemEspCategory category : ItemEspCategory.values()) {
            if (!category.matches(stack)) continue;
            return category;
        }
        return null;
    }
}
