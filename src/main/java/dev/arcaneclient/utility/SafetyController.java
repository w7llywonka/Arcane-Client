package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.combat.CombatController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/** One combined, edge-triggered warning policy. It never closes the active connection. */
@Environment(EnvType.CLIENT)
public final class SafetyController {
    private static SafetyRules.Reason latchedReason = SafetyRules.Reason.NONE;

    private SafetyController() {
    }

    public static void reset() {
        latchedReason = SafetyRules.Reason.NONE;
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        ClientPlayerEntity player = client.player;
        if (config == null || player == null || client.world == null || !config.safetyDisconnect) {
            reset();
            return;
        }

        int totems = CombatController.totemCount(player);
        int weakestArmor = weakestArmorPercent(player);
        int elytraDurability = elytraDurabilityPercent(player);
        int rockets = ElytraAssistController.rocketCount(player);
        boolean playerNearby = client.world.getPlayers().stream()
            .filter(other -> other != player && !other.isSpectator())
            .anyMatch(other -> SafetyRules.nearby(other.squaredDistanceTo(player), config.nearbyPlayerRange));

        SafetyRules.Reason reason = SafetyRules.firstTriggered(
            config.safetyRuleHealth,
            config.safetyRuleTotems,
            config.safetyRuleArmor,
            config.safetyRuleFlight,
            config.safetyRuleProximity,
            SafetyRules.lowHealth(player.getHealth(), config.lowHealthHearts),
            SafetyRules.lowTotems(totems, config.safetyTotemMinimum),
            SafetyRules.lowArmor(weakestArmor, config.armorAlertPercent),
            player.isGliding() && SafetyRules.flightUnsafe(
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

    private static int weakestArmorPercent(ClientPlayerEntity player) {
        int weakest = 100;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) return 0;
            if (stack.isDamageable()) weakest = Math.min(weakest, durabilityPercent(stack));
        }
        return weakest;
    }

    private static int elytraDurabilityPercent(ClientPlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isDamageable() ? durabilityPercent(chest) : 100;
    }

    private static int durabilityPercent(ItemStack stack) {
        return (stack.getMaxDamage() - stack.getDamage()) * 100 / Math.max(1, stack.getMaxDamage());
    }
}
