package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Automatically spends hotbar or off-hand rockets to maintain efficient Elytra flight. */
@Environment(EnvType.CLIENT)
public final class ElytraAssistController {
    private static int boostCooldown;
    private static boolean missingRocketsLatched;

    private ElytraAssistController() {
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        LocalPlayer player = client.player;
        if (boostCooldown > 0) boostCooldown--;
        if (config == null || player == null || client.level == null || client.gameMode == null) {
            reset();
            return;
        }

        boolean inputBlocked = client.gui.screen() != null
            || player.isUsingItem()
            || client.options.keyUse.isDown()
            || FreecamController.isActive();
        double speedBlocksPerSecond = player.getDeltaMovement().length() * 20.0;
        if (!ElytraAssistLogic.shouldBoost(
            config.elytraAssist,
            player.isFallFlying(),
            inputBlocked,
            boostCooldown,
            speedBlocksPerSecond,
            config.elytraAssistSmartConservation,
            config.elytraAssistBoostBelow
        )) {
            if (!config.elytraAssist || !player.isFallFlying()) {
                boostCooldown = 0;
                missingRocketsLatched = false;
                releaseHotbarLease(InventoryActionScheduler.shared());
            }
            return;
        }

        InteractionHand hand = InteractionHand.OFF_HAND;
        int rocketSlot = -1;
        if (!player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            hand = InteractionHand.MAIN_HAND;
            rocketSlot = findRocketSlot(player);
        }
        if (hand == InteractionHand.MAIN_HAND && rocketSlot < 0) {
            if (!missingRocketsLatched) {
                player.sendOverlayMessage(Component.literal("Elytra Assist: no rockets in hotbar"));
                missingRocketsLatched = true;
            }
            boostCooldown = 20;
            return;
        }

        missingRocketsLatched = false;
        long tick = InventoryAutomationSupport.tick(player);
        if (!acquireHotbarLease(InventoryActionScheduler.shared(), tick)) return;
        int previousSlot = player.getInventory().getSelectedSlot();
        boolean changedSlot = hand == InteractionHand.MAIN_HAND && rocketSlot != previousSlot;
        try {
            if (changedSlot) setSelectedSlot(client, player, rocketSlot);
            InteractionResult result = client.gameMode.useItem(player, hand);
            if (result.consumesAction()) {
                player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
                boostCooldown = config.elytraAssistDelayTicks;
            } else {
                boostCooldown = 5;
            }
        } finally {
            if (changedSlot) setSelectedSlot(client, player, previousSlot);
        }
    }

    public static void reset() {
        boostCooldown = 0;
        missingRocketsLatched = false;
        releaseHotbarLease(InventoryActionScheduler.shared());
    }

    public static int cooldownTicks() {
        return boostCooldown;
    }

    public static int rocketCount(LocalPlayer player) {
        int count = player.getOffhandItem().is(Items.FIREWORK_ROCKET)
            ? player.getOffhandItem().getCount()
            : 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.FIREWORK_ROCKET)) count += stack.getCount();
        }
        return count;
    }

    private static int findRocketSlot(LocalPlayer player) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (player.getInventory().getItem(selectedSlot).is(Items.FIREWORK_ROCKET)) {
            return selectedSlot;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(Items.FIREWORK_ROCKET)) return slot;
        }
        return -1;
    }

    private static void setSelectedSlot(Minecraft client, LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    static boolean acquireHotbarLease(InventoryActionScheduler scheduler, long tick) {
        return scheduler.tryAcquire(
            InventoryActionScheduler.Owner.ELYTRA_ASSIST,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            tick,
            1
        );
    }

    static void releaseHotbarLease(InventoryActionScheduler scheduler) {
        scheduler.releaseAll(InventoryActionScheduler.Owner.ELYTRA_ASSIST);
    }
}
