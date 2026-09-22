package dev.arcaneclient.additions.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import dev.arcaneclient.mixin.MinecraftClientAccessor;
import dev.arcaneclient.mixin.additions.HandledScreenAccessor;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Independently authored, bounded utility actions using ordinary player-menu swaps. */
@Environment(EnvType.CLIENT)
public final class UtilityAdditions {
    private static ClientLevel previousWorld;
    private static Screen previousScreen;
    private static int hoveredSlot = -1;
    private static long hoverReadyTick;
    private static long nextInventoryTick;
    private static String swapStatus = "Click once to swap";
    private static final Map<BlockPos, Long> spawnerAlerts = new LinkedHashMap<>();

    private UtilityAdditions() {
    }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        UtilityAdditionsConfig settings = config.utilityAdditions;
        return List.of(
            GuiModule.toggle("Elytra Swap", "Click once to exchange your chestplate and elytra. Main-inventory swaps require an empty unselected hotbar slot.",
                    () -> false, value -> { if (value) swapElytra(client); })
                .with(new GuiSetting.Info("Status", () -> swapStatus)).build(),
            GuiModule.toggle("Hover Totem", "Hover over a totem in your player inventory to swap it into the offhand after a short delay.",
                    () -> settings.hoverTotem, value -> settings.hoverTotem = value)
                .with(new GuiSetting.Slider("Hover delay", () -> settings.hoverTotemDelayTicks, value -> settings.hoverTotemDelayTicks = value, 1, 20, "t")).build(),
            GuiModule.toggle("Auto Inventory Totem", "Keeps one reserve totem in an empty, unselected hotbar slot. Existing hotbar items stay in place.",
                    () -> settings.inventoryTotem, value -> settings.inventoryTotem = value)
                .with(new GuiSetting.Slider("Action delay", () -> settings.inventoryTotemDelayTicks, value -> settings.inventoryTotemDelayTicks = value, 2, 40, "t")).build(),
            GuiModule.toggle("Fast Use", "Caps the repeat-use delay while you hold use. Food, charging and server item cooldowns keep their normal duration.",
                    () -> settings.fastUse, value -> settings.fastUse = value)
                .with(new GuiSetting.Slider("Repeat delay", () -> settings.fastUseDelayTicks, value -> settings.fastUseDelayTicks = value, 1, 4, "t")).build(),
            GuiModule.toggle("Spawner Protect", "Alerts when another actor's break progress reaches a loaded spawner. Alerts do not prevent block damage or theft.",
                    () -> settings.spawnerProtect, value -> settings.spawnerProtect = value)
                .with(new GuiSetting.Info("Action", () -> "ALERT ONLY"))
                .with(new GuiSetting.Slider("Range", () -> settings.spawnerAlertRange, value -> settings.spawnerAlertRange = value, 8, 128, "m"))
                .with(new GuiSetting.Slider("Alert cooldown", () -> settings.spawnerAlertCooldownSeconds, value -> settings.spawnerAlertCooldownSeconds = value, 2, 60, "s")).build()
        );
    }

    public static void tick(Minecraft client) {
        if (client.level != previousWorld) {
            reset(client);
            previousWorld = client.level;
        }
        if (client.level == null || client.player == null || ArcaneClient.config() == null) {
            reset(client);
            return;
        }
        if (client.gui.screen() != previousScreen) {
            previousScreen = client.gui.screen();
            clearInventoryState();
            return;
        }
        UtilityAdditionsConfig settings = ArcaneClient.config().utilityAdditions;
        if (!usablePlayer(client) || client.isPaused() || DetachedCameraInteraction.isActive()) {
            clearInventoryState();
            return;
        }
        if (settings.hoverTotem) tickHoverTotem(client, settings);
        else {
            hoveredSlot = -1;
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.HOVER_TOTEM);
        }
        if (settings.inventoryTotem) tickInventoryTotem(client, settings);
        else InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.INVENTORY_TOTEM);
        if (settings.fastUse && client.gui.screen() == null && client.options.keyUse.isDown()
            && !client.player.isUsingItem()
            && (!client.player.getMainHandItem().isEmpty() || !client.player.getOffhandItem().isEmpty())) {
            MinecraftClientAccessor access = (MinecraftClientAccessor) client;
            int delay = Math.clamp(settings.fastUseDelayTicks, 1, 4);
            if (access.arcaneclient$getItemUseCooldown() > delay) access.arcaneclient$setItemUseCooldown(delay);
        }
        if (!settings.spawnerProtect) spawnerAlerts.clear();
    }

    public static void reset(Minecraft client) {
        previousWorld = null;
        previousScreen = null;
        swapStatus = "Click once to swap";
        spawnerAlerts.clear();
        clearInventoryState();
    }

    private static void clearInventoryState() {
        hoveredSlot = -1;
        hoverReadyTick = 0;
        nextInventoryTick = 0;
        InventoryActionScheduler scheduler = InventoryActionScheduler.shared();
        scheduler.releaseAll(InventoryActionScheduler.Owner.ELYTRA_SWAP);
        scheduler.releaseAll(InventoryActionScheduler.Owner.HOVER_TOTEM);
        scheduler.releaseAll(InventoryActionScheduler.Owner.INVENTORY_TOTEM);
    }

    private static boolean usablePlayer(Minecraft client) {
        return client.player != null && client.player.isAlive() && !client.player.isSpectator()
            && !client.player.isCreative() && client.gameMode != null;
    }

    private static boolean automaticInventoryAllowed(Minecraft client) {
        return (client.gui.screen() == null || client.gui.screen() instanceof InventoryScreen)
            && !client.player.isUsingItem()
            && InventoryAutomationSupport.canUsePlayerInventory(client, client.player);
    }

    private static void tickHoverTotem(Minecraft client, UtilityAdditionsConfig settings) {
        LocalPlayer player = client.player;
        if (!(client.gui.screen() instanceof InventoryScreen screen) || !automaticInventoryAllowed(client)
            || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            hoveredSlot = -1;
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.HOVER_TOTEM);
            return;
        }
        Slot source = ((HandledScreenAccessor) screen).arcaneclient$utilityFocusedSlot();
        if (source == null || source.container != player.getInventory() || source.getContainerSlot() < 0
            || source.getContainerSlot() >= 36 || !source.getItem().is(Items.TOTEM_OF_UNDYING)
            || !source.isActive() || !source.mayPickup(player)
            || (!player.getOffhandItem().isEmpty() && !source.mayPlace(player.getOffhandItem()))) {
            hoveredSlot = -1;
            return;
        }
        long tick = InventoryAutomationSupport.tick(player);
        if (hoveredSlot != source.index) {
            hoveredSlot = source.index;
            hoverReadyTick = tick + Math.clamp(settings.hoverTotemDelayTicks, 1, 20);
            return;
        }
        if (tick < hoverReadyTick || !acquire(InventoryActionScheduler.Owner.HOVER_TOTEM, player, 4)) return;
        client.gameMode.handleContainerInput(player.inventoryMenu.containerId, source.index, 40, ContainerInput.SWAP, player);
        hoveredSlot = -1;
    }

    private static void tickInventoryTotem(Minecraft client, UtilityAdditionsConfig settings) {
        LocalPlayer player = client.player;
        if (!automaticInventoryAllowed(client)) return;
        long tick = InventoryAutomationSupport.tick(player);
        if (tick < nextInventoryTick) return;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) return;
        }
        int destination = emptyUnselectedHotbar(player);
        if (destination < 0) return;
        for (int index = 9; index < 36; index++) {
            if (!player.getInventory().getItem(index).is(Items.TOTEM_OF_UNDYING)) continue;
            Slot source = player.inventoryMenu.getSlot(InventoryAutomationSupport.playerMenuSlot(index));
            if (!source.mayPickup(player) || !acquire(InventoryActionScheduler.Owner.INVENTORY_TOTEM, player, 2)) return;
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, source.index, destination, ContainerInput.SWAP, player);
            nextInventoryTick = tick + Math.clamp(settings.inventoryTotemDelayTicks, 2, 40);
            return;
        }
    }

    private static int emptyUnselectedHotbar(LocalPlayer player) {
        for (int index = 0; index < 9; index++) {
            if (index != player.getInventory().getSelectedSlot() && player.getInventory().getItem(index).isEmpty()) return index;
        }
        return -1;
    }

    private static boolean acquire(InventoryActionScheduler.Owner owner, LocalPlayer player, int ticks) {
        return InventoryActionScheduler.shared().tryAcquire(owner, InventoryActionScheduler.Channel.INVENTORY_CLICK,
            InventoryAutomationSupport.tick(player), ticks);
    }

    private static void swapElytra(Minecraft client) {
        if (!usablePlayer(client) || client.level == null || client.player.isUsingItem() || client.player.isFallFlying()
            || DetachedCameraInteraction.isActive() || !InventoryAutomationSupport.canUsePlayerInventory(client, client.player)) {
            reportSwap(client, "Swap unavailable while using items, gliding or holding a cursor stack");
            return;
        }
        LocalPlayer player = client.player;
        Slot chest = player.inventoryMenu.getSlot(InventoryAutomationSupport.armorMenuSlot(EquipmentSlot.CHEST));
        boolean wantElytra = !chest.getItem().is(Items.ELYTRA);
        int inventoryIndex = findChestItem(player, wantElytra);
        if (inventoryIndex < 0) {
            reportSwap(client, wantElytra ? "No usable elytra in inventory" : "No unbound chestplate in inventory");
            return;
        }
        Slot source = player.inventoryMenu.getSlot(InventoryAutomationSupport.playerMenuSlot(inventoryIndex));
        if (!chest.mayPickup(player) || !source.mayPickup(player) || !chest.mayPlace(source.getItem())
            || (!chest.getItem().isEmpty() && !source.mayPlace(chest.getItem()))) {
            reportSwap(client, "The equipped item cannot be exchanged");
            return;
        }
        int hotbarSlot = inventoryIndex < 9 ? inventoryIndex : emptyUnselectedHotbar(player);
        if (hotbarSlot < 0) {
            reportSwap(client, "Free an unselected hotbar slot for this exchange");
            return;
        }
        if (!acquire(InventoryActionScheduler.Owner.ELYTRA_SWAP, player, 3)) {
            reportSwap(client, "Another inventory action is active; click again");
            return;
        }
        // SWAP never places stacks on the cursor. The temporary slot was empty, so
        // the final swap puts the old chest item back into the source inventory slot.
        int syncId = player.inventoryMenu.containerId;
        if (inventoryIndex >= 9) client.gameMode.handleContainerInput(syncId, source.index, hotbarSlot, ContainerInput.SWAP, player);
        client.gameMode.handleContainerInput(syncId, chest.index, hotbarSlot, ContainerInput.SWAP, player);
        if (inventoryIndex >= 9) client.gameMode.handleContainerInput(syncId, source.index, hotbarSlot, ContainerInput.SWAP, player);
        reportSwap(client, wantElytra ? "Equipped elytra" : "Equipped chestplate");
    }

    private static int findChestItem(LocalPlayer player, boolean elytra) {
        int bestIndex = -1;
        double bestScore = -1;
        for (int index = 0; index < 36; index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (stack.isEmpty() || stack.getCount() != 1 || stack.is(Items.ELYTRA) != elytra) continue;
            Equippable equipment = stack.get(DataComponents.EQUIPPABLE);
            if (equipment == null || equipment.slot() != EquipmentSlot.CHEST) continue;
            if (stack.getEnchantments().entrySet().stream().anyMatch(entry -> entry.getKey().is(Enchantments.BINDING_CURSE))) continue;
            int remaining = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 1;
            if (remaining <= (elytra ? 1 : 0)) continue;
            ItemAttributeModifiers attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            double armor = attributes == null ? 0 : attributes.compute(Attributes.ARMOR, 0, EquipmentSlot.CHEST);
            if (!elytra && armor <= 0) continue;
            double score = armor * 10000 + remaining;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static void reportSwap(Minecraft client, String message) {
        swapStatus = message;
        if (client.player != null) client.player.sendOverlayMessage(Component.literal("Elytra Swap: " + message));
    }

    public static void onBlockBreaking(int entityId, BlockPos pos, int progress) {
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.utilityAdditions.spawnerProtect || client.player == null
            || client.level == null || client.level != previousWorld || progress < 0 || progress > 9
            || entityId == client.player.getId() || !client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        UtilityAdditionsConfig settings = config.utilityAdditions;
        var state = client.level.getBlockState(pos);
        if (!state.is(Blocks.SPAWNER) && !state.is(Blocks.TRIAL_SPAWNER)) return;
        int range = Math.clamp(settings.spawnerAlertRange, 8, 128);
        if (client.player.distanceToSqr(Vec3.atCenterOf(pos)) > (double) range * range) return;
        long tick = client.player.tickCount;
        Long last = spawnerAlerts.get(pos);
        if (last != null && tick - last < Math.clamp(settings.spawnerAlertCooldownSeconds, 2, 60) * 20L) return;
        if (spawnerAlerts.size() >= 64 && !spawnerAlerts.containsKey(pos)) spawnerAlerts.remove(spawnerAlerts.keySet().iterator().next());
        spawnerAlerts.put(pos.immutable(), tick);
        String location = config.streamerMode ? "nearby" : "at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        client.player.sendSystemMessage(Component.literal("[Arcane] Spawner break activity " + location + ". Alert only; block damage is not prevented."));
    }
}
