package dev.arcaneclient.scan;

import dev.arcaneclient.model.SignalCategory;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

@Environment(EnvType.CLIENT)
public final class GrowthTransitions {
    private GrowthTransitions() {
    }

    public static GrowthEvent analyze(BlockState before, BlockState after) {
        if (before.hasBlockEntity() || after.hasBlockEntity()) return null;
        String beforeId = ActivityClassifier.blockPath(before);
        String afterId = ActivityClassifier.blockPath(after);
        if ((!growingBlock(beforeId) && !growingBlock(afterId)) || isObfuscationMask(beforeId)) return null;
        return analyze(
            beforeId, properties(before), afterId, properties(after)
        );
    }

    static GrowthEvent analyze(String beforeId, Map<String, Object> before, String afterId, Map<String, Object> after) {
        if (excluded(beforeId) || excluded(afterId)) {
            return null;
        }
        int oldAmethyst = amethystRank(beforeId);
        int newAmethyst = amethystRank(afterId);
        if (oldAmethyst >= 0 || newAmethyst >= 0) {
            if (oldAmethyst >= 0 && newAmethyst == oldAmethyst + 1
                && java.util.Objects.equals(before.get("facing"), after.get("facing"))) {
                return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, "amethyst bud advanced", Family.AMETHYST);
            }
            // Initial appearances and skipped stages can be reveals, not observed growth.
            return null;
        }

        if (isObfuscationMask(beforeId)) return null;

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

        if (afterId.equals("kelp") && empty(beforeId)) {
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 30, "kelp column grew", Family.KELP);
        }
        if (isKelp(beforeId) && empty(afterId)) {
            return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 115, "kelp column harvested", Family.KELP);
        }
        if (beforeId.equals("kelp") && afterId.equals("kelp_plant")) {
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

        if (isCrop(beforeId) && oldAge != null && newAge != null && beforeId.equals(afterId) && newAge > oldAge) {
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 18, "crop growth tick: " + beforeId, Family.CROP);
        }
        if (isCrop(beforeId) && oldAge != null && oldAge > 0 && empty(afterId)) {
            return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 140, "mature plant removed: " + beforeId, Family.CROP);
        }
        if (isVerticalCrop(afterId) && empty(beforeId)) {
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, afterId + " grew vertically", Family.VERTICAL_PLANT);
        }
        if (isVerticalCrop(beforeId) && empty(afterId)) {
            return new GrowthEvent(SignalCategory.LIVE_ACTIVITY, 105, beforeId + " harvested", Family.VERTICAL_PLANT);
        }
        if (beforeId.equals(afterId) && beforeId.equals("vine")) {
            boolean spread = false;
            for (String face : java.util.List.of("north", "south", "east", "west", "up")) {
                if (Boolean.TRUE.equals(before.get(face)) && !Boolean.TRUE.equals(after.get(face))) return null;
                spread |= Boolean.FALSE.equals(before.get(face)) && Boolean.TRUE.equals(after.get(face));
            }
            if (spread) return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, "vine spread", Family.CAVE_VINE);
        }
        if ((beforeId.equals("cave_vines") || beforeId.equals("weeping_vines") || beforeId.equals("twisting_vines"))
            && afterId.equals(beforeId + "_plant"))
            return new GrowthEvent(SignalCategory.NATURAL_GROWTH, 24, "vine stem extended", Family.CAVE_VINE);
        return null;
    }

    private static boolean empty(String id) {
        return id.equals("air") || id.equals("cave_air") || id.equals("water");
    }

    private static boolean growingBlock(String id) {
        return isCrop(id) || isVerticalCrop(id) || isKelp(id) || id.equals("sweet_berry_bush") || amethystRank(id) >= 0;
    }

    private static boolean excluded(String id) {
        return ScannerStorageFilter.isStoragePath(id) || id.endsWith("_sapling") || id.equals("mangrove_propagule")
            || id.equals("beehive") || id.equals("bee_nest");
    }

    private static boolean isCrop(String id) {
        return switch (id) {
            case "wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa", "melon_stem",
                 "pumpkin_stem", "torchflower_crop", "pitcher_crop", "cactus", "sugar_cane" -> true;
            default -> false;
        };
    }

    public static int amethystRank(String id) {
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
        return id.equals("bamboo") || id.equals("vine") || id.startsWith("cave_vines") || id.equals("cactus") || id.equals("sugar_cane")
            || id.startsWith("weeping_vines") || id.startsWith("twisting_vines");
    }

    private static boolean isObfuscationMask(String id) {
        return switch (id) {
            case "stone", "deepslate", "tuff", "andesite", "diorite", "granite", "calcite", "smooth_basalt" -> true;
            default -> false;
        };
    }

    private static Map<String, Object> properties(BlockState state) {
        java.util.HashMap<String, Object> values = new java.util.HashMap<>();
        Iterator<Property.Value<?>> entries = state.getValues().iterator();
        while (entries.hasNext()) {
            Property.Value<?> entry = entries.next();
            values.put(entry.property().getName(), entry.value());
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
