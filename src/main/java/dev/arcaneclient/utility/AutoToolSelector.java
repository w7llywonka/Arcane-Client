package dev.arcaneclient.utility;

/** Deterministic hotbar scoring used by Auto Tool and its tests. */
public final class AutoToolSelector {
    private static final float SUITABLE_BONUS = 10_000.0f;

    private AutoToolSelector() {
    }

    public static int choose(int currentSlot, float[] miningSpeeds, boolean[] suitable, boolean[] eligible) {
        if (miningSpeeds.length != 9 || suitable.length != 9 || eligible.length != 9) {
            throw new IllegalArgumentException("Auto Tool requires exactly nine hotbar candidates");
        }
        int bestSlot = -1;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int offset = 0; offset < 9; offset++) {
            int slot = (currentSlot + offset) % 9;
            if (!eligible[slot]) continue;
            float score = miningSpeeds[slot] + (suitable[slot] ? SUITABLE_BONUS : 0.0f);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot < 0 ? currentSlot : bestSlot;
    }
}
