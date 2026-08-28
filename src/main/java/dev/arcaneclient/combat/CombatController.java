package dev.arcaneclient.combat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.FreecamController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

/** Runtime behavior for Arcane's combat and survival modules. */
@Environment(EnvType.CLIENT)
public final class CombatController {
    private static boolean lowHealthLatched;
    private static boolean armorLatched;
    private static boolean autoEating;
    private static int previousSlot = -1;
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
            lowHealthLatched = false;
            armorLatched = false;
            return;
        }
        if (alertCooldown > 0) alertCooldown--;
        tickAutoSprint(client, player, config);
        tickAutoEat(client, player, config);
        tickLowHealth(client, player, config);
        tickArmor(client, player, config);
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
        boolean needsFood = player.getHungerManager().getFoodLevel() <= config.autoEatHunger;
        if (!config.autoEat || !needsFood || client.currentScreen != null || player.isSpectator()) {
            stopAutoEat(client, player);
            return;
        }
        if (!autoEating) {
            int foodSlot = findFood(player);
            if (foodSlot < 0) return;
            previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(foodSlot);
            autoEating = true;
        }
        client.options.useKey.setPressed(true);
    }

    private static void stopAutoEat(MinecraftClient client, ClientPlayerEntity player) {
        if (!autoEating) return;
        client.options.useKey.setPressed(false);
        if (player != null && previousSlot >= 0 && previousSlot < 9) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        autoEating = false;
    }

    private static int findFood(ClientPlayerEntity player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.contains(DataComponentTypes.FOOD)) return slot;
        }
        return -1;
    }

    private static void tickLowHealth(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        boolean low = config.lowHealthAlert && CombatMath.lowHealth(player.getHealth(), config.lowHealthHearts);
        if (low && !lowHealthLatched) notify(client, "Low health: " + Math.round(player.getHealth() / 2.0f) + " hearts", 0.65f);
        lowHealthLatched = low;
    }

    private static void tickArmor(MinecraftClient client, ClientPlayerEntity player, ArcaneConfig config) {
        int weakest = 100;
        boolean wearingArmor = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            wearingArmor = true;
            weakest = Math.min(weakest, CombatMath.durabilityPercent(stack.getDamage(), stack.getMaxDamage()));
        }
        boolean low = config.armorAlert && wearingArmor && weakest <= config.armorAlertPercent;
        if (low && !armorLatched) notify(client, "Armor durability: " + weakest + "%", 0.8f);
        armorLatched = low;
    }

    public static int totemCount(ClientPlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) count += player.getOffHandStack().getCount();
        return count;
    }

    public static void notify(MinecraftClient client, String message, float pitch) {
        if (alertCooldown > 0 || client.player == null) return;
        client.player.sendMessage(Text.literal(message), true);
        play(client, pitch);
        alertCooldown = 20;
    }

    private static void play(MinecraftClient client, float pitch) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.soundNotifications || config.notificationVolume <= 0) return;
        float volume = config.notificationVolume / 100.0f;
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, pitch, volume));
    }
}
