package dev.arcaneclient.combat;

/** Pure combat thresholds shared by controllers and tests. */
public final class CombatMath {
    private CombatMath() {
    }

    public static int durabilityPercent(int damage, int maxDamage) {
        if (maxDamage <= 0) return 100;
        return Math.clamp(Math.round((maxDamage - Math.clamp(damage, 0, maxDamage)) * 100.0f / maxDamage), 0, 100);
    }

    public static boolean lowHealth(float health, int hearts) {
        return health <= Math.clamp(hearts, 1, 10) * 2.0f;
    }
}
