package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.combat.CombatController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** One combined, edge-triggered warning policy. It never closes the active connection. */
@Environment(EnvType.CLIENT)
public final class SafetyController {
    private static SafetyRules.Reason latchedReason = SafetyRules.Reason.NONE;

    private SafetyController() {
    }

    public static void reset() {
        latchedReason = SafetyRules.Reason.NONE;
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        LocalPlayer player = client.player;
        if (config == null || player == null || client.level == null || !config.safetyDisconnect) {
            reset();
            return;
        }

        int totems = CombatController.totemCount(player);
        int weakestArmor = weakestArmorPercent(player);
        int elytraDurability = elytraDurabilityPercent(player);
        int rockets = ElytraAssistController.rocketCount(player);
        boolean playerNearby = client.level.players().stream()
            .filter(other -> other != player && !other.isSpectator())
            .anyMatch(other -> SafetyRules.nearby(other.distanceToSqr(player), config.nearbyPlayerRange));

        SafetyRules.Reason reason = SafetyRules.firstTriggered(
            config.safetyRuleHealth,
            config.safetyRuleTotems,
            config.safetyRuleArmor,
            config.safetyRuleFlight,
            config.safetyRuleProximity,
            SafetyRules.lowHealth(player.getHealth(), config.lowHealthHearts),
            SafetyRules.lowTotems(totems, config.safetyTotemMinimum),
            SafetyRules.lowArmor(weakestArmor, config.armorAlertPercent),
            player.isFallFlying() && SafetyRules.flightUnsafe(
                elytraDurability,
                rockets,
                config.flightSafetyDurability,
                config.flightSafetyRockets
            ),
            playerNearby
        );

        if (reason == SafetyRules.Reason.NONE) {
            latchedReason = SafetyRules.Reason.NONE;
            return;
        }
        if (reason == latchedReason) return;
        if (CombatController.notify(client, "Safety alert: " + reason.label(), 0.55f)) {
            latchedReason = reason;
        }
    }

    private static int weakestArmorPercent(LocalPlayer player) {
        int weakest = 100;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) return 0;
            if (stack.isDamageableItem()) weakest = Math.min(weakest, durabilityPercent(stack));
        }
        return weakest;
    }

    private static int elytraDurabilityPercent(LocalPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return chest.isDamageableItem() ? durabilityPercent(chest) : 100;
    }

    private static int durabilityPercent(ItemStack stack) {
        return (stack.getMaxDamage() - stack.getDamageValue()) * 100 / Math.max(1, stack.getMaxDamage());
    }
}
