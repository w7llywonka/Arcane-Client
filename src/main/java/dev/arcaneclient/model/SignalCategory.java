package dev.arcaneclient.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public enum SignalCategory {
    NATURAL_GROWTH(35),
    CULTIVATION(65),
    PLACED_BLOCK(0),
    INTERACTION(0),
    INFRASTRUCTURE(0),
    LIGHT_LEAK(0),
    LIVE_ACTIVITY(0),
    ENTITY(0),
    BLOCK_ENTITY(0);

    private final int cap;

    private SignalCategory(int cap) {
        this.cap = cap;
    }

    public int cap() {
        return this.cap;
    }
}
