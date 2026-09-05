package dev.arcaneclient.utility;

/** Pure threshold rules shared by the safety controller and focused unit tests. */
public final class SafetyRules {
    private SafetyRules() {
    }

    public static boolean durabilityCritical(int damage, int maxDamage, int remainingThreshold) {
        return maxDamage > 0 && maxDamage - damage <= Math.clamp(remainingThreshold, 1, 25);
    }

    public static boolean nearby(double squaredDistance, int range) {
        int clamped = Math.clamp(range, 8, 160);
        return squaredDistance <= (double) clamped * clamped;
    }

    public static boolean flightUnsafe(int durabilityPercent, int rockets, int durabilityThreshold, int rocketThreshold) {
        return durabilityPercent <= Math.clamp(durabilityThreshold, 1, 50)
            || rockets <= Math.clamp(rocketThreshold, 1, 32);
    }

    public static boolean lowHealth(float health, int hearts) {
        return health <= Math.clamp(hearts, 1, 10) * 2.0f;
    }

    public static boolean lowTotems(int totems, int minimum) {
        return totems <= Math.clamp(minimum, 0, 8);
    }

    public static boolean lowArmor(int weakestPercent, int thresholdPercent) {
        return weakestPercent <= Math.clamp(thresholdPercent, 1, 100);
    }

    public static Reason firstTriggered(
        boolean healthRule,
        boolean totemRule,
        boolean armorRule,
        boolean flightRule,
        boolean proximityRule,
        boolean healthUnsafe,
        boolean totemsUnsafe,
        boolean armorUnsafe,
        boolean flightUnsafe,
        boolean proximityUnsafe
    ) {
        if (healthRule && healthUnsafe) return Reason.HEALTH;
        if (totemRule && totemsUnsafe) return Reason.TOTEMS;
        if (armorRule && armorUnsafe) return Reason.ARMOR;
        if (flightRule && flightUnsafe) return Reason.FLIGHT;
        if (proximityRule && proximityUnsafe) return Reason.PROXIMITY;
        return Reason.NONE;
    }

    public enum Reason {
        NONE("Safe"),
        HEALTH("low health"),
        TOTEMS("totem reserve"),
        ARMOR("armor durability"),
        FLIGHT("flight reserves"),
        PROXIMITY("nearby player");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }
}
