package dev.arcaneclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WorldObservationTest {
    @Test
    void preservesTheFourExactAmethystGrowthStages() {
        for (int stage = 0; stage <= 3; stage++) {
            WorldObservation observation = new WorldObservation(
                new BlockPosition(stage, -20, 0),
                WorldObservation.Kind.AMETHYST_SHARD,
                100,
                true,
                stage
            );
            assertEquals(stage, observation.stage());
        }
    }

    @Test
    void nonAmethystObservationsKeepUnknownStage() {
        WorldObservation observation = new WorldObservation(
            new BlockPosition(0, -20, 0),
            WorldObservation.Kind.COBBLED_DEEPSLATE_TRAIL,
            90,
            false
        );
        assertEquals(-1, observation.stage());
    }
}
