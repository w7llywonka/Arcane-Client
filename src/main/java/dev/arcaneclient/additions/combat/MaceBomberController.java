package dev.arcaneclient.additions.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;

/** One ordinary, charged attack during a real descent; never changes movement or reach. */
final class MaceBomberController {
    private static final InventoryActionScheduler.Owner OWNER = InventoryActionScheduler.Owner.MACE_BOMBER;
    private static LocalPlayer selectedPlayer;
    private static int previousSlot = -1;
    private static int selectedSlot = -1;
    private static int switchedAt = Integer.MIN_VALUE;
    private static int attackedAt = Integer.MIN_VALUE;
    private static boolean restoreSlot;
    private static boolean attackedThisFall;
    private static boolean cancelledThisFall;

    private MaceBomberController() { }

    static void tick(Minecraft client, CombatAdditionsConfig config, boolean attackHeld, boolean useHeld) {
        if (!falling(client.player)) {
            reset(client);
            return;
        }
        if (!config.maceBomber || useHeld || config.maceBomberRequireAttack && !attackHeld
            || attackedThisFall || cancelledThisFall) {
            suspend(client);
            return;
        }
        LocalPlayer player = client.player;
        if (selectedPlayer != null && (selectedPlayer != player
            || player.getInventory().getSelectedSlot() != selectedSlot
            || !usableMace(player.getInventory().getItem(selectedSlot)))) {
            // A manual slot change or replacement item wins for the rest of this descent.
            cancelledThisFall = true;
            suspend(client);
            return;
        }

        int slot = maceSlot(client, config);
        if (slot < 0) { suspend(client); return; }
        int original = player.getInventory().getSelectedSlot();
        if (!InventoryAutomationSupport.selectHotbar(client, player, OWNER, slot, 2)) {
            // A higher-priority inventory action keeps its selection.
            suspend(client);
            return;
        }
        if (selectedPlayer == null) {
            selectedPlayer = player;
            previousSlot = original;
            selectedSlot = slot;
            switchedAt = original == slot ? Integer.MIN_VALUE : player.tickCount;
            restoreSlot = config.maceBomberRestore;
        }
        if (player.tickCount == switchedAt || player.fallDistance < config.maceBomberFallDistance
            || !MaceItem.canSmashAttack(player)
            || player.getAttackStrengthScale(0.0f) < 0.95f) return;

        var target = CombatAdditionsController.clickTarget(client);
        if (target == null || !player.isWithinEntityInteractionRange(target, 0.0)) return;
        // The ordinary interaction manager sends exactly one attack with the owned mace
        // selected. Manual weapon-preparation hooks cannot replace it with an axe/spear.
        attackedThisFall = true;
        attackedAt = player.tickCount;
        try {
            client.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
        } finally {
            suspend(client);
        }
    }

    static boolean controlsAttack(Minecraft client, CombatAdditionsConfig config) {
        // A successful smash may immediately clear fallDistance; still reserve this tick
        // so an Auto Clicker that ignores cooldown cannot follow it with a second attack.
        if (config.maceBomber && client.player != null && client.player.tickCount == attackedAt) return true;
        if (!config.maceBomber || !falling(client.player) || cancelledThisFall
            || CombatAdditionsController.physical(client, client.options.keyUse)
            || config.maceBomberRequireAttack && !CombatAdditionsController.physical(client, client.options.keyAttack)
            || CombatAdditionsController.clickTarget(client) == null) return false;
        return attackedThisFall || maceSlot(client, config) >= 0;
    }

    private static boolean falling(LocalPlayer player) {
        return player != null && !player.onGround() && player.fallDistance > 0
            && player.getDeltaMovement().y < -0.08 && !player.isFallFlying() && !player.isInWater()
            && !player.isInLava() && !player.onClimbable() && !player.isPassenger() && !player.getAbilities().flying;
    }

    private static int maceSlot(Minecraft client, CombatAdditionsConfig config) {
        int selected = client.player.getInventory().getSelectedSlot();
        if (usableMace(client.player.getInventory().getItem(selected))) return selected;
        return config.maceBomberAutoMace ? CombatAdditionsController.findHotbar(client, MaceBomberController::usableMace) : -1;
    }

    private static boolean usableMace(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.MACE)
            && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 3);
    }

    static void suspend(Minecraft client) {
        if (client != null && selectedPlayer != null && client.player == selectedPlayer
            && restoreSlot && previousSlot >= 0 && selectedPlayer.getInventory().getSelectedSlot() == selectedSlot
            && InventoryActionScheduler.shared().isOwnedBy(OWNER,
                InventoryActionScheduler.Channel.HOTBAR_SELECTION, selectedPlayer.tickCount)) {
            InventoryAutomationSupport.selectHotbar(client, selectedPlayer, OWNER, previousSlot, 1);
        }
        selectedPlayer = null;
        previousSlot = selectedSlot = -1;
        switchedAt = Integer.MIN_VALUE;
        restoreSlot = false;
        InventoryActionScheduler.shared().releaseAll(OWNER);
    }

    static void reset(Minecraft client) {
        suspend(client);
        attackedThisFall = cancelledThisFall = false;
        attackedAt = Integer.MIN_VALUE;
    }
}
