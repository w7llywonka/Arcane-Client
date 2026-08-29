package dev.arcaneclient.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class BlockEntityEspClassifier {
    public static final int DEBUG_COLOR = -218103809;

    private BlockEntityEspClassifier() {
    }

    public static int color(String id, boolean storageEsp, boolean debugEsp) {
        int storageColor = BlockEntityEspClassifier.storageColor(id);
        if (storageEsp && storageColor != 0) {
            return storageColor;
        }
        return debugEsp ? -218103809 : 0;
    }

    public static boolean isStorageTarget(String id) {
        return BlockEntityEspClassifier.storageColor(id) != 0;
    }

    public static boolean isContainerTarget(String id) {
        return id.contains("chest") || id.equals("barrel") || id.contains("shulker") || id.endsWith("shelf")
            || id.equals("hopper") || id.equals("crafter") || id.equals("dispenser") || id.equals("dropper")
            || id.contains("furnace") || id.equals("smoker") || id.equals("brewing_stand")
            || id.equals("lectern") || id.equals("jukebox") || id.equals("beehive");
    }

    private static int storageColor(String id) {
        if (id.equals("mob_spawner") || id.equals("trial_spawner") || id.equals("vault")) {
            return -218152880;
        }
        if (id.equals("beacon") || id.equals("ender_chest") || id.equals("end_gateway")) {
            return -218147352;
        }
        if (id.contains("chest") || id.equals("barrel") || id.contains("shulker") || id.endsWith("shelf")) {
            return -230627073;
        }
        if (id.equals("hopper") || id.equals("crafter") || id.equals("dispenser") || id.equals("dropper") || id.contains("furnace") || id.equals("smoker") || id.equals("brewing_stand") || id.equals("lectern") || id.equals("jukebox") || id.equals("beehive")) {
            return -218125771;
        }
        return 0;
    }
}
