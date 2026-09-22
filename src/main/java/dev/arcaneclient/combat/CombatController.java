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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

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
            if (config != null && config.hitSound && !FreecamController.isActive() && client.hitResult instanceof EntityHitResult) {
                play(client, 1.45f);
            }
            return false;
        });
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            stopAutoEat(client, null);
            return;
        }
        if (alertCooldown > 0) alertCooldown--;
        tickAutoSprint(client, player, config);
        tickAutoEat(client, player, config);
    }

    private static void tickAutoSprint(Minecraft client, LocalPlayer player, ArcaneConfig config) {
        if (!config.autoSprint || client.gui.screen() != null) return;
        boolean shouldSprint = client.options.keyUp.isDown()
            && !client.options.keyShift.isDown()
            && !player.isUsingItem()
            && !player.horizontalCollision
            && player.getFoodData().getFoodLevel() > 6;
        if (shouldSprint) player.setSprinting(true);
    }

    private static void tickAutoEat(Minecraft client, LocalPlayer player, ArcaneConfig config) {
        boolean needsFood = player.getFoodData().getFoodLevel() <= config.autoEatHunger
            && player.getFoodData().getFoodLevel() < 20;
        if (!config.autoEat || !needsFood || client.gui.screen() != null || player.isSpectator()) {
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
            || !isSafeAutoFood(player.getInventory().getItem(autoEatSlot))
            || !InventoryActionScheduler.shared().tryAcquire(
            InventoryActionScheduler.Owner.AUTO_EAT,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION,
            InventoryAutomationSupport.tick(player),
            2
        )) {
            stopAutoEat(client, player);
            return;
        }
        client.options.keyUse.setDown(true);
    }

    private static void stopAutoEat(Minecraft client, LocalPlayer player) {
        if (!autoEating) {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AUTO_EAT);
            return;
        }
        client.options.keyUse.setDown(false);
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

    private static int findFood(LocalPlayer player) {
        ArrayList<AutoEatPolicy.Candidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            candidates.add(new AutoEatPolicy.Candidate(slot, food.nutrition(), food.saturation(), isSafeAutoFood(stack)));
        }
        return AutoEatPolicy.choose(player.getFoodData().getFoodLevel(), candidates);
    }

    private static boolean isSafeAutoFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD)
            && !stack.is(Items.ROTTEN_FLESH)
            && !stack.is(Items.SPIDER_EYE)
            && !stack.is(Items.POISONOUS_POTATO)
            && !stack.is(Items.PUFFERFISH)
            && !stack.is(Items.CHICKEN)
            && !stack.is(Items.SUSPICIOUS_STEW)
            && !stack.is(Items.CHORUS_FRUIT)
            && !stack.is(Items.GOLDEN_APPLE)
            && !stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    public static void reset(Minecraft client) {
        stopAutoEat(client, client == null ? null : client.player);
        alertCooldown = 0;
    }

    static void suspendAutoEat(Minecraft client) {
        stopAutoEat(client, client == null ? null : client.player);
    }

    public static int totemCount(LocalPlayer player) {
        int count = 0;
        // Main inventory/hotbar occupy indices 0..35. PlayerInventory also exposes
        // armor and off-hand slots, so iterating size() and then adding off-hand
        // again would double-count the equipped totem.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) count += player.getOffhandItem().getCount();
        return count;
    }

    public static boolean notify(Minecraft client, String message, float pitch) {
        if (alertCooldown > 0 || client.player == null) return false;
        client.player.sendOverlayMessage(Component.literal(message));
        play(client, pitch);
        alertCooldown = 20;
        return true;
    }

    private static void play(Minecraft client, float pitch) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.soundNotifications || config.notificationVolume <= 0) return;
        float volume = config.notificationVolume / 100.0f;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, volume));
    }
}
