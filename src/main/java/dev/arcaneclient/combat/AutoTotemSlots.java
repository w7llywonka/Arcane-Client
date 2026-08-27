package dev.arcaneclient.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class AutoTotemSlots {
    private AutoTotemSlots() {
    }

    public static int inventoryMenuSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= 36) {
            throw new IllegalArgumentException("inventory index must be between 0 and 35");
        }
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }
}
