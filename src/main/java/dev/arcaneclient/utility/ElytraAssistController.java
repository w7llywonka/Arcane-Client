package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/** Automatically spends hotbar or off-hand rockets to maintain efficient Elytra flight. */
@Environment(EnvType.CLIENT)
public final class ElytraAssistController {
    private static int boostCooldown;
    private static boolean missingRocketsLatched;

    private ElytraAssistController() {
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        ClientPlayerEntity player = client.player;
        if (boostCooldown > 0) boostCooldown--;
        if (config == null || player == null || client.world == null || client.interactionManager == null) {
            reset();
            return;
        }

        boolean inputBlocked = client.currentScreen != null
            || player.isUsingItem()
            || client.options.useKey.isPressed()
            || FreecamController.isActive();
        double speedBlocksPerSecond = player.getVelocity().length() * 20.0;
        if (!ElytraAssistLogic.shouldBoost(
            config.elytraAssist,
            player.isGliding(),
            inputBlocked,
            boostCooldown,
            speedBlocksPerSecond,
            config.elytraAssistSmartConservation,
            config.elytraAssistBoostBelow
        )) {
            if (!config.elytraAssist || !player.isGliding()) {
                boostCooldown = 0;
                missingRocketsLatched = false;
            }
            return;
        }

        Hand hand = Hand.OFF_HAND;
        int rocketSlot = -1;
        if (!player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            hand = Hand.MAIN_HAND;
            rocketSlot = findRocketSlot(player);
        }
        if (hand == Hand.MAIN_HAND && rocketSlot < 0) {
            if (!missingRocketsLatched) {
                player.sendMessage(Text.literal("Elytra Assist: no rockets in hotbar"), true);
                missingRocketsLatched = true;
            }
            boostCooldown = 20;
            return;
        }

        missingRocketsLatched = false;
        int previousSlot = player.getInventory().getSelectedSlot();
        boolean changedSlot = hand == Hand.MAIN_HAND && rocketSlot != previousSlot;
        try {
            if (changedSlot) setSelectedSlot(client, player, rocketSlot);
            ActionResult result = client.interactionManager.interactItem(player, hand);
            if (result.isAccepted()) {
                player.swingHand(hand);
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
    }

    public static int cooldownTicks() {
        return boostCooldown;
    }

    public static int rocketCount(ClientPlayerEntity player) {
        int count = player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)
            ? player.getOffHandStack().getCount()
            : 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.FIREWORK_ROCKET)) count += stack.getCount();
        }
        return count;
    }

    private static int findRocketSlot(ClientPlayerEntity player) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (player.getInventory().getStack(selectedSlot).isOf(Items.FIREWORK_ROCKET)) {
            return selectedSlot;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.FIREWORK_ROCKET)) return slot;
        }
        return -1;
    }

    private static void setSelectedSlot(MinecraftClient client, ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }
}
