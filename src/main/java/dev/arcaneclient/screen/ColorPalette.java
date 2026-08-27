package dev.arcaneclient.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class ColorPalette {
    private static final int[] COLORS = new int[]{-218147499, -218130091, -218117069, -223674539, -229245014, -229253633, -229275393, -223713537, -218147363, -218103809};

    private ColorPalette() {
    }

    public static int next(int color) {
        for (int index = 0; index < COLORS.length; ++index) {
            if (COLORS[index] != color) continue;
            return COLORS[(index + 1) % COLORS.length];
        }
        return COLORS[0];
    }
}
