package dev.arcaneclient.additions.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A synchronous axe lease covering the actual vanilla/trigger attack packet. */
public final class ShieldBreakerController {
    private static LocalPlayer ownerPlayer;
    private static int previousSlot = -1;
    private static int selectedSlot = -1;

    private ShieldBreakerController() { }

    public static boolean prepare(Minecraft client, Entity target, boolean enabled) {
        if (!enabled || !CombatAdditionsController.ready(client)
            || !(target instanceof LivingEntity living) || !living.isBlocking()
            || !living.getUseItem().is(Items.SHIELD)
            || !CombatAdditionsController.validLivingTarget(client, living)
            || !client.player.isWithinAttackRange(client.player.getMainHandItem(), living.getBoundingBox(), 0.0)) return false;
        int current = client.player.getInventory().getSelectedSlot();
        int axeSlot = usableAxe(client.player.getInventory().getItem(current)) ? current : -1;
        for (int slot = 0; slot < 9 && axeSlot < 0; slot++) {
            if (usableAxe(client.player.getInventory().getItem(slot))) axeSlot = slot;
        }
        if (axeSlot < 0 || !InventoryAutomationSupport.selectHotbar(client, client.player,
            InventoryActionScheduler.Owner.SHIELD_BREAKER, axeSlot, 1)) return false;
        ownerPlayer = client.player;
        previousSlot = current;
        selectedSlot = axeSlot;
        return true;
    }

    public static void restore(Minecraft client) {
        if (client != null && ownerPlayer != null && client.player == ownerPlayer
            && previousSlot >= 0 && ownerPlayer.getInventory().getSelectedSlot() == selectedSlot
            && InventoryActionScheduler.shared().isOwnedBy(InventoryActionScheduler.Owner.SHIELD_BREAKER,
                InventoryActionScheduler.Channel.HOTBAR_SELECTION, ownerPlayer.tickCount)) {
            InventoryAutomationSupport.selectHotbar(client, ownerPlayer,
                InventoryActionScheduler.Owner.SHIELD_BREAKER, previousSlot, 1);
        }
        ownerPlayer = null;
        previousSlot = selectedSlot = -1;
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.SHIELD_BREAKER);
    }

    private static boolean usableAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.AXES)
            && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 3);
    }
}
