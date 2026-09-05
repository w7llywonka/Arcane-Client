package dev.arcaneclient.combat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

/** Runtime behavior for Arcane's combat and survival modules. */
@Environment(EnvType.CLIENT)
public final class CombatController {
    private static boolean autoEating;
    private static int previousSlot = -1;
    private static int autoEatSlot = -1;
    private static int alertCooldown;

    private CombatController() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            ArcaneConfig config = ArcaneClient.config();
            if (config != null && config.hitSound && !FreecamController.isActive() && client.crosshairTarget instanceof EntityHitResult) {
                play(client, 1.45f);
            }
            return false;
        });
    }

    public static void tick(MinecraftClient client) {
        ArcaneConfig config = ArcaneClient.config();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            stopAutoEat(client, null);
            return;
        }
        if (alertCooldown > 0) alertCooldown--;
        tickAutoSprint(client, player, config);
        tickAutoEat(client, player, config);
    }

    private static void tickAutoSprint(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        if (!config.autoSprint || client.currentScreen != null) return;
        boolean shouldSprint = client.options.forwardKey.isPressed()
            && !client.options.sneakKey.isPressed()
            && !player.isUsingItem()
            && !player.horizontalCollision
            && player.getHungerManager().getFoodLevel() > 6;
        if (shouldSprint) player.setSprinting(true);
    }

    private static void tickAutoEat(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        boolean needsFood = player.getHungerManager().getFoodLevel() <= config.autoEatHunger
            && player.getHungerManager().getFoodLevel() < 20;
        if (!config.autoEat || !needsFood || client.currentScreen != null || player.isSpectator()) {
            stopAutoEat(client, player);
            return;
        }
        if (!autoEating && player.isUsingItem()) return;
        if (!autoEating) {
            int foodSlot = findFood(player);
            if (foodSlot < 0) return;
            previousSlot = player.getInventory().getSelectedSlot();
            if (!InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.AUTO_EAT,
                foodSlot,
                2
            )) {
                previousSlot = -1;
                return;
            }
            autoEatSlot = foodSlot;
            autoEating = true;
        } else if (player.getInventory().getSelectedSlot() != autoEatSlot
            || !isSafeAutoFood(player.getInventory().getStack(autoEatSlot))
            || !InventoryActionScheduler.shared().tryAcquire(
            InventoryActionScheduler.Owner.AUTO_EAT,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            InventoryAutomationSupport.tick(player),
            2
        )) {
            stopAutoEat(client, player);
            return;
        }
        client.options.useKey.setPressed(true);
    }

    private static void stopAutoEat(MinecraftClient client, ClientPlayerEntity player) {
        if (!autoEating) {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_EAT);
            return;
        }
        client.options.useKey.setPressed(false);
        if (player != null && previousSlot >= 0 && previousSlot < 9
            && player.getInventory().getSelectedSlot() == autoEatSlot) {
            InventoryAutomationSupport.selectHotbar(
                client,
                player,
                InventoryActionScheduler.Owner.AUTO_EAT,
                previousSlot,
                1
            );
        }
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_EAT);
        previousSlot = -1;
        autoEatSlot = -1;
        autoEating = false;
    }

    private static int findFood(ClientPlayerEntity player) {
        ArrayList<AutoEatPolicy.Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null) continue;
            candidates.add(new AutoEatPolicy.Candidate(slot, food.nutrition(), food.saturation(), isSafeAutoFood(stack)));
        }
        return AutoEatPolicy.choose(player.getHungerManager().getFoodLevel(), candidates);
    }

    private static boolean isSafeAutoFood(ItemStack stack) {
        return stack.contains(DataComponentTypes.FOOD)
            && !stack.isOf(Items.ROTTEN_FLESH)
            && !stack.isOf(Items.SPIDER_EYE)
            && !stack.isOf(Items.POISONOUS_POTATO)
            && !stack.isOf(Items.PUFFERFISH)
            && !stack.isOf(Items.CHICKEN)
            && !stack.isOf(Items.SUSPICIOUS_STEW)
            && !stack.isOf(Items.CHORUS_FRUIT)
            && !stack.isOf(Items.GOLDEN_APPLE)
            && !stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
    }

    public static void reset(MinecraftClient client) {
        stopAutoEat(client, client == null ? null : client.player);
        alertCooldown = 0;
    }

    static void suspendAutoEat(MinecraftClient client) {
        stopAutoEat(client, client == null ? null : client.player);
    }

    public static int totemCount(ClientPlayerEntity player) {
        int count = 0;
        // Main inventory/hotbar occupy indices 0..35. PlayerInventory also exposes
        // armor and off-hand slots, so iterating size() and then adding off-hand
        // again would double-count the equipped totem.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) count += player.getOffHandStack().getCount();
        return count;
    }

    public static boolean notify(MinecraftClient client, String message, float pitch) {
        if (alertCooldown > 0 || client.player == null) return false;
        client.player.sendMessage(Text.literal(message), true);
        play(client, pitch);
        alertCooldown = 20;
        return true;
    }

    private static void play(MinecraftClient client, float pitch) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.soundNotifications || config.notificationVolume <= 0) return;
        float volume = config.notificationVolume / 100.0f;
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, pitch, volume));
    }
}
