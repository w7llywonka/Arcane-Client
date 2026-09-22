package dev.arcaneclient.scan;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Explicit block-state allowlist. Counts blocks, not inferred farms or player activity. */
public final class GrownBlocks {
    private static final Map<BlockState, Boolean> CACHE = new IdentityHashMap<>();
    private GrownBlocks() {}

    public static boolean matches(BlockState state) {
        return CACHE.computeIfAbsent(state, GrownBlocks::classify);
    }

    private static boolean classify(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return false;
        String id = ActivityClassifier.blockPath(state);
        int age = -1;
        boolean berries = false;
        String half = "";
        Iterator<Property.Value<?>> values = state.getValues().iterator();
        while (values.hasNext()) {
            Property.Value<?> property = values.next();
            Object value = property.value();
            switch (property.property().getName()) {
                case "age" -> { if (value instanceof Integer number) age = number; }
                case "berries" -> berries = Boolean.TRUE.equals(value);
                case "half" -> half = value.toString();
                default -> { }
            }
        }
        return matches(id, age, berries, half);
    }

    static boolean matches(String id, int age, boolean berries, String half) {
        return switch (id) {
            case "amethyst_cluster", "kelp_plant", "vine", "weeping_vines_plant", "twisting_vines_plant",
                 "bamboo", "sugar_cane", "cactus", "attached_melon_stem", "attached_pumpkin_stem" -> true;
            case "cave_vines", "cave_vines_plant" -> berries;
            case "sweet_berry_bush" -> age >= 2;
            case "wheat", "carrots", "potatoes", "melon_stem", "pumpkin_stem" -> age >= 7;
            case "beetroots", "nether_wart" -> age >= 3;
            case "cocoa" -> age >= 2;
            case "pitcher_crop" -> age >= 4 && !half.equals("upper");
            default -> false;
        };
    }
}
