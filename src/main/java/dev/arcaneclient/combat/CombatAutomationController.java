package dev.arcaneclient.combat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.utility.AutoFishController;
import dev.arcaneclient.utility.HotbarRefillController;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Coordinates inventory-affecting combat modules and guarantees one explicit
 * weapon precedence: Mace, then Spear, then general Smart Weapon.
 *
 * <p>Manual attacks must call {@link #prepareForManualAttack} at the head of
 * the vanilla attack method and {@link #restoreAfterManualAttack} at return,
 * so the server receives the attack packet while the selected weapon is still
 * active. Trigger Bot uses the same preparation path internally.</p>
 */
@Environment(EnvType.CLIENT)
public final class CombatAutomationController {
    private enum PreparedWeapon {
        NONE,
        MACE,
        SPEAR,
        SMART
    }

    private static final int SAFE_MINIMUM_DURABILITY_PERCENT = 3;
    private static final int INVENTORY_ACTION_DELAY_TICKS = 4;
    private static final float TRIGGER_MINIMUM_CHARGE = 0.92f;
    private static final double SPEAR_MINIMUM_DISTANCE = 2.0;
    private static final double SPEAR_MAXIMUM_DISTANCE = 4.5;
    private static final double MACE_MINIMUM_FALL_DISTANCE = 1.5;
    private static final double MACE_MAXIMUM_VERTICAL_VELOCITY = -0.08;
    private static final double MACE_MINIMUM_TARGET_DROP = 0.35;
    private static final double FISH_BITE_VELOCITY = -0.12;

    private static PreparedWeapon preparedWeapon = PreparedWeapon.NONE;

    private CombatAutomationController() {
    }

    public static void tick(MinecraftClient client) {
        tick(client, entity -> false);
    }

    /** Runs passive inventory automation and Trigger Bot once per client tick. */
    public static void tick(MinecraftClient client, Predicate<Entity> protectedTarget) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(protectedTarget, "protectedTarget");
        ArcaneConfig config = ArcaneClient.config();
        if (config == null) return;

        AutoArmorController.tick(client, autoArmorSettings(config));
        HotbarRefillController.tick(client, hotbarRefillSettings(config));
        AutoFishController.tick(client, autoFishSettings(config));

        try {
            TriggerBotController.tick(
                client,
                triggerSettings(config),
                protectedTarget,
                target -> prepareForTarget(client, target, config)
            );
        } finally {
            // Trigger Bot sends its attack packet synchronously inside tick().
            restorePreparedWeapon(client);
        }
    }

    /** Selects the correct weapon for a crosshair entity before a manual attack packet. */
    public static void prepareForManualAttack(MinecraftClient client) {
        Objects.requireNonNull(client, "client");
        restorePreparedWeapon(client);
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !(client.crosshairTarget instanceof EntityHitResult hit)) return;
        prepareForTarget(client, hit.getEntity(), config);
    }

    /** Restores the player's prior hotbar slot after the manual attack packet was sent. */
    public static void restoreAfterManualAttack(MinecraftClient client) {
        Objects.requireNonNull(client, "client");
        restorePreparedWeapon(client);
    }

    public static void reset(MinecraftClient client) {
        if (client != null) restorePreparedWeapon(client);
        preparedWeapon = PreparedWeapon.NONE;
        AutoArmorController.reset();
        HotbarRefillController.reset();
        AutoFishController.reset(client != null && client.player != null ? client.player.age : 0L);
        TriggerBotController.reset();
        InventoryActionScheduler.shared().reset();
    }

    private static void prepareForTarget(MinecraftClient client, Entity target, ArcaneConfig config) {
        restorePreparedWeapon(client);
        CombatController.suspendAutoEat(client);
        if (MaceSwitchController.prepareForTarget(client, target, maceSettings(config))) {
            preparedWeapon = PreparedWeapon.MACE;
            return;
        }
        if (SpearSwitchController.prepareForTarget(client, target, spearSettings(config))) {
            preparedWeapon = PreparedWeapon.SPEAR;
            return;
        }
        if (SmartWeaponController.prepareForTarget(client, target, smartWeaponSettings(config))) {
            preparedWeapon = PreparedWeapon.SMART;
        }
    }

    private static void restorePreparedWeapon(MinecraftClient client) {
        switch (preparedWeapon) {
            case MACE -> MaceSwitchController.restore(client);
            case SPEAR -> SpearSwitchController.restore(client);
            case SMART -> SmartWeaponController.restore(client);
            case NONE -> { }
        }
        preparedWeapon = PreparedWeapon.NONE;
    }

    private static AutoArmorController.Settings autoArmorSettings(ArcaneConfig config) {
        return new AutoArmorController.Settings(
            config.autoArmor,
            Math.max(1, config.autoArmorDelayTicks),
            SAFE_MINIMUM_DURABILITY_PERCENT,
            0.25,
            false,
            false,
            false,
            false
        );
    }

    private static SmartWeaponController.Settings smartWeaponSettings(ArcaneConfig config) {
        return new SmartWeaponController.Settings(
            config.smartWeapon,
            SmartWeaponPolicy.Mode.HIT_DAMAGE,
            SAFE_MINIMUM_DURABILITY_PERCENT,
            true,
            Math.max(2, config.smartWeaponDelayTicks + 1)
        );
    }

    private static TriggerBotController.Settings triggerSettings(ArcaneConfig config) {
        return new TriggerBotController.Settings(
            config.triggerBot,
            new TriggerBotPolicy.Settings(
                TRIGGER_MINIMUM_CHARGE,
                Math.max(0, config.triggerBotDelayTicks),
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                true
            )
        );
    }

    private static SpearSwitchController.Settings spearSettings(ArcaneConfig config) {
        return new SpearSwitchController.Settings(
            config.spearSwitch,
            SPEAR_MINIMUM_DISTANCE,
            SPEAR_MAXIMUM_DISTANCE,
            SAFE_MINIMUM_DURABILITY_PERCENT,
            2
        );
    }

    private static MaceSwitchController.Settings maceSettings(ArcaneConfig config) {
        return new MaceSwitchController.Settings(
            config.maceSwitch,
            MACE_MINIMUM_FALL_DISTANCE,
            MACE_MAXIMUM_VERTICAL_VELOCITY,
            MACE_MINIMUM_TARGET_DROP,
            SAFE_MINIMUM_DURABILITY_PERCENT,
            2
        );
    }

    private static HotbarRefillController.Settings hotbarRefillSettings(ArcaneConfig config) {
        return new HotbarRefillController.Settings(
            config.hotbarRefill,
            Math.max(1, config.hotbarRefillThreshold),
            INVENTORY_ACTION_DELAY_TICKS,
            false,
            false
        );
    }

    private static AutoFishController.Settings autoFishSettings(ArcaneConfig config) {
        return new AutoFishController.Settings(
            config.autoFish,
            true,
            4,
            8,
            FISH_BITE_VELOCITY,
            SAFE_MINIMUM_DURABILITY_PERCENT
        );
    }
}
