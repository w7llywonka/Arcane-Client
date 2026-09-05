package dev.arcaneclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CombatAutomationPoliciesTest {
    @Test
    void autoArmorChoosesLargestSafeUpgrade() {
        var iron = armor(9, AutoArmorPolicy.Slot.CHEST, 6.0, 80, false, false);
        var diamond = armor(10, AutoArmorPolicy.Slot.CHEST, 8.0, 70, false, false);
        var binding = armor(11, AutoArmorPolicy.Slot.HEAD, 20.0, 100, true, false);
        var elytra = armor(12, AutoArmorPolicy.Slot.CHEST, 30.0, 100, false, true);
        var equipped = armor(-1, AutoArmorPolicy.Slot.CHEST, 5.0, 100, false, false);

        var result = AutoArmorPolicy.chooseUpgrade(
            List.of(iron, diamond, binding, elytra),
            List.of(equipped),
            new AutoArmorPolicy.Settings(15, 0.0, false, false)
        );

        assertEquals(10, result.orElseThrow().inventoryIndex());
    }

    @Test
    void autoArmorDoesNotFightAnEquippedBindingCurse() {
        var upgrade = armor(9, AutoArmorPolicy.Slot.HEAD, 20.0, 100, false, false);
        var locked = armor(-1, AutoArmorPolicy.Slot.HEAD, 1.0, 100, true, false);

        assertTrue(AutoArmorPolicy.chooseUpgrade(
            List.of(upgrade),
            List.of(locked),
            new AutoArmorPolicy.Settings(0, 0.0, false, false)
        ).isEmpty());
    }

    @Test
    void autoArmorProtectsAnEquippedElytraByDefault() {
        var chestplate = armor(9, AutoArmorPolicy.Slot.CHEST, 20.0, 100, false, false);
        var equippedElytra = armor(-1, AutoArmorPolicy.Slot.CHEST, 0.0, 80, false, true);

        assertTrue(AutoArmorPolicy.chooseUpgrade(
            List.of(chestplate),
            List.of(equippedElytra),
            new AutoArmorPolicy.Settings(0, 0.0, false, false)
        ).isEmpty());
    }

    @Test
    void autoEatPrefersUsefulFoodAndNeverRunsAtFullHunger() {
        var largeMeal = new AutoEatPolicy.Candidate(1, 8, 12.8f, true);
        var smallMeal = new AutoEatPolicy.Candidate(2, 2, 0.4f, true);
        var unsafe = new AutoEatPolicy.Candidate(0, 20, 20.0f, false);

        assertEquals(1, AutoEatPolicy.choose(8, List.of(unsafe, smallMeal, largeMeal)));
        assertEquals(2, AutoEatPolicy.choose(19, List.of(unsafe, smallMeal, largeMeal)));
        assertEquals(-1, AutoEatPolicy.choose(20, List.of(smallMeal, largeMeal)));
    }

    @Test
    void smartWeaponHonorsDurabilityAndShieldBreakerPreference() {
        var sword = new SmartWeaponPolicy.Candidate(0, 9.0, 1.6, 1.0, 0.0, false, 100, true);
        var axe = new SmartWeaponPolicy.Candidate(2, 8.0, 1.0, 0.0, 0.0, true, 60, true);
        var broken = new SmartWeaponPolicy.Candidate(3, 30.0, 1.0, 0.0, 0.0, false, 1, true);
        var settings = new SmartWeaponPolicy.Settings(SmartWeaponPolicy.Mode.HIT_DAMAGE, 10, true);

        assertEquals(2, SmartWeaponPolicy.choose(
            0,
            List.of(sword, axe, broken),
            new SmartWeaponPolicy.Target(12.0, true),
            settings
        ));
    }

    @Test
    void triggerBotRequiresAllowedSafeReadyTarget() {
        var settings = new TriggerBotPolicy.Settings(
            0.9f, 2, true, true, false, false, true, true, true, true
        );
        var ready = triggerContext(TriggerBotPolicy.TargetKind.HOSTILE, false, false, true, false, 1.0f, 3);
        assertTrue(TriggerBotPolicy.shouldAttack(ready, settings));
        assertFalse(TriggerBotPolicy.shouldAttack(
            triggerContext(TriggerBotPolicy.TargetKind.PLAYER, true, false, true, false, 1.0f, 3),
            settings
        ));
        assertFalse(TriggerBotPolicy.shouldAttack(
            triggerContext(TriggerBotPolicy.TargetKind.HOSTILE, false, false, true, false, 0.4f, 3),
            settings
        ));
    }

    @Test
    void spearOnlySwitchesInsideConfiguredAndWeaponReach() {
        var settings = new SpearSwitchPolicy.Settings(2.0, 4.5, 10);
        assertFalse(SpearSwitchPolicy.isUsefulDistance(1.99, 2.0, 4.5, settings));
        assertTrue(SpearSwitchPolicy.isUsefulDistance(2.25, 2.0, 4.5, settings));
        assertFalse(SpearSwitchPolicy.isUsefulDistance(4.51, 2.0, 4.5, settings));
        assertEquals(4, SpearSwitchPolicy.choose(
            0,
            List.of(
                new SpearSwitchPolicy.Candidate(2, 6, 5),
                new SpearSwitchPolicy.Candidate(4, 5, 90),
                new SpearSwitchPolicy.Candidate(6, 4, 100)
            ),
            settings
        ));
    }

    @Test
    void maceRequiresAnActualDownwardSmashOpportunity() {
        var settings = new MaceSwitchPolicy.Settings(1.5, -0.08, 0.5, 10);
        assertTrue(MaceSwitchPolicy.shouldSwitch(
            new MaceSwitchPolicy.Context(2.0, -0.4, 1.0, false, false, false),
            settings
        ));
        assertFalse(MaceSwitchPolicy.shouldSwitch(
            new MaceSwitchPolicy.Context(2.0, -0.4, 1.0, true, false, false),
            settings
        ));
        assertFalse(MaceSwitchPolicy.shouldSwitch(
            new MaceSwitchPolicy.Context(2.0, 0.1, 1.0, false, false, false),
            settings
        ));
    }

    private static AutoArmorPolicy.Candidate armor(
        int index,
        AutoArmorPolicy.Slot slot,
        double armor,
        int durability,
        boolean binding,
        boolean elytra
    ) {
        return new AutoArmorPolicy.Candidate(index, slot, armor, 0.0, 0.0, 0, 0, durability, binding, elytra);
    }

    private static TriggerBotPolicy.Context triggerContext(
        TriggerBotPolicy.TargetKind kind,
        boolean protectedTarget,
        boolean tamed,
        boolean visible,
        boolean usingItem,
        float charge,
        int elapsed
    ) {
        return new TriggerBotPolicy.Context(
            kind, true, true, false, protectedTarget, false, tamed, visible, usingItem, false, false, charge, elapsed
        );
    }
}
