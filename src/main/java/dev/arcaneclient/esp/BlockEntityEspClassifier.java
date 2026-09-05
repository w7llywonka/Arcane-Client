package dev.arcaneclient.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class BlockEntityEspClassifier {
    /** Fully deepslate terrain begins below zero; Y=0 and above are never debug targets. */
    public static final int DEEPSLATE_LEVEL_Y = 0;
    public static final int DEBUG_COLOR = -218103809;

    private BlockEntityEspClassifier() {
    }

    public static int color(String id) {
        return BlockEntityEspClassifier.storageColor(id);
    }

    public static boolean isStorageTarget(String id) {
        return BlockEntityEspClassifier.storageColor(id) != 0;
    }

    public static int debugColor(int blockY) {
        return isBelowDeepslateLevel(blockY) ? DEBUG_COLOR : 0;
    }

    public static boolean isBelowDeepslateLevel(int blockY) {
        return blockY < DEEPSLATE_LEVEL_Y;
    }

    public static boolean isContainerTarget(String id) {
        return id.contains("chest") || id.equals("barrel") || id.contains("shulker") || id.endsWith("shelf")
            || id.equals("hopper") || id.equals("crafter") || id.equals("dispenser") || id.equals("dropper")
            || id.contains("furnace") || id.equals("smoker") || id.equals("brewing_stand")
            || id.equals("lectern") || id.equals("jukebox") || id.equals("beehive");
    }

    private static int storageColor(String id) {
        if (id.contains("chest") || id.equals("barrel") || id.contains("shulker") || id.endsWith("shelf")) {
            return -230627073;
        }
        if (id.equals("hopper") || id.equals("crafter") || id.equals("dispenser") || id.equals("dropper") || id.contains("furnace") || id.equals("smoker") || id.equals("brewing_stand") || id.equals("lectern") || id.equals("jukebox") || id.equals("beehive")) {
            return -218125771;
        }
        return 0;
    }
}
