package dev.arcaneclient.scan;

import dev.arcaneclient.model.SignalCategory;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

@Environment(EnvType.CLIENT)
public final class GrowthTransitions {
    private GrowthTransitions() {
    }

    public static GrowthEvent analyze(BlockState before, BlockState after) {
        return analyze(
            ActivityClassifier.blockPath(before), properties(before),
            ActivityClassifier.blockPath(after), properties(after)
        );
    }

    static GrowthEvent analyze(String beforeId, Map<String, Object> before, String afterId, Map<String, Object> after) {
        int oldAmethyst = amethystRank(beforeId);
        int newAmethyst = amethystRank(afterId);
        if (oldAmethyst >= 0 || newAmethyst >= 0) {
            if (newAmethyst > oldAmethyst) {
                return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 38, "amethyst stage advanced: " + afterId, Family.AMETHYST);
            }
            if (oldAmethyst >= 0 && newAmethyst < 0) {
                return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 145, "amethyst growth removed", Family.AMETHYST);
            }
        }

        Integer oldAge = integer(before, "age");
        Integer newAge = integer(after, "age");
        if (beforeId.equals("sweet_berry_bush") && afterId.equals(beforeId) && oldAge != null && newAge != null) {
            if (oldAge >= 3 && newAge <= 1) {
                return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 165, "sweet berries harvested", Family.BERRY);
            }
            if (newAge > oldAge) {
                return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, "sweet berry growth tick", Family.BERRY);
            }
        }

        if (isKelp(afterId) && !isKelp(beforeId)) {
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 30, "kelp column grew", Family.KELP);
        }
        if (isKelp(beforeId) && !isKelp(afterId)) {
            return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 115, "kelp column harvested", Family.KELP);
        }
        if (isKelp(beforeId) && isKelp(afterId) && oldAge != null && newAge != null && !oldAge.equals(newAge)) {
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 22, "kelp growth tick", Family.KELP);
        }

        if (beforeId.startsWith("cave_vines") && afterId.startsWith("cave_vines")) {
            Boolean oldBerries = bool(before, "berries");
            Boolean newBerries = bool(after, "berries");
            if (Boolean.TRUE.equals(oldBerries) && Boolean.FALSE.equals(newBerries)) {
                return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 145, "glow berries harvested", Family.CAVE_VINE);
            }
            if (Boolean.FALSE.equals(oldBerries) && Boolean.TRUE.equals(newBerries)) {
                return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, "glow berries matured", Family.CAVE_VINE);
            }
        }

        if ((beforeId.equals("beehive") || beforeId.equals("bee_nest")) && afterId.equals(beforeId)) {
            Integer oldHoney = integer(before, "honey_level");
            Integer newHoney = integer(after, "honey_level");
            if (oldHoney != null && newHoney != null) {
                if (oldHoney >= 5 && newHoney == 0) {
                    return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 155, "honey harvested", Family.HONEY);
                }
                if (newHoney > oldHoney) {
                    return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 18, "hive honey increased", Family.HONEY);
                }
            }
        }

        if (oldAge != null && newAge != null && beforeId.equals(afterId) && !oldAge.equals(newAge)) {
            if (newAge < oldAge) {
                return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 125, "crop harvested or reset: " + beforeId, Family.CROP);
            }
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 18, "crop growth tick: " + beforeId, Family.CROP);
        }
        if (oldAge != null && oldAge > 0 && (afterId.equals("air") || afterId.equals("water"))) {
            return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 140, "mature plant removed: " + beforeId, Family.CROP);
        }
        if (isVerticalCrop(afterId) && !afterId.equals(beforeId) && (beforeId.equals("air") || beforeId.equals("water"))) {
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, afterId + " grew vertically", Family.VERTICAL_PLANT);
        }
        if (isVerticalCrop(beforeId) && !beforeId.equals(afterId)) {
            return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 105, beforeId + " harvested", Family.VERTICAL_PLANT);
        }
        return null;
    }

    static int amethystRank(String id) {
        return switch (id) {
            case "small_amethyst_bud" -> 0;
            case "medium_amethyst_bud" -> 1;
            case "large_amethyst_bud" -> 2;
            case "amethyst_cluster" -> 3;
            default -> -1;
        };
    }

    private static boolean isKelp(String id) {
        return id.equals("kelp") || id.equals("kelp_plant");
    }

    private static boolean isVerticalCrop(String id) {
        return id.equals("bamboo") || id.equals("bamboo_sapling") || id.equals("cactus") || id.equals("sugar_cane")
            || id.startsWith("weeping_vines") || id.startsWith("twisting_vines");
    }

    private static Map<String, Object> properties(BlockState state) {
        java.util.HashMap<String, Object> values = new java.util.HashMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            values.put(entry.getKey().getName(), entry.getValue());
        }
        return values;
    }

    private static Integer integer(Map<String, Object> values, String name) {
        Object value = values.get(name);
        return value instanceof Integer integer ? integer : null;
    }

    private static Boolean bool(Map<String, Object> values, String name) {
        Object value = values.get(name);
        return value instanceof Boolean bool ? bool : null;
    }

    public record GrowthEvent(SignalCategory category, int strength, String reason, Family family) {
    }

    public enum Family {
        AMETHYST,
        KELP,
        BERRY,
        CAVE_VINE,
        HONEY,
        CROP,
        VERTICAL_PLANT
    }
}
