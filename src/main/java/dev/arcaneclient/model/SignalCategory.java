package dev.arcaneclient.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public enum SignalCategory {
    GROWN_PLANTS(100),
    GROWTH_ACTIVITY(75),
    PLANT_HARVEST(25),
    NATURAL_GROWTH(6),
    CULTIVATION(16),
    PLACED_BLOCK(30),
    INTERACTION(24),
    INFRASTRUCTURE(24),
    LIGHT_LEAK(24),
    LIVE_ACTIVITY(30),
    ENTITY(30),
    BLOCK_ENTITY(30);

    private final int cap;

    private SignalCategory(int cap) {
        this.cap = cap;
    }

    public int cap() {
        return this.cap;
    }
}
