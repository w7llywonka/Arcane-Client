package dev.arcaneclient.additions.dispenser;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Ordinary container clicks with exact item checks and server-confirmed pickup/place steps. */
public final class DispenserHelper {
    private enum Phase { IDLE, READY, PICKUP, PLACE }
    private record Move(int containerSlot, int inventorySlot, ItemStack stack) { }
    private static final InventoryActionScheduler.Owner OWNER = InventoryActionScheduler.Owner.DISPENSER_HELPER;
    private static final InventoryActionScheduler.Channel CHANNEL = InventoryActionScheduler.Channel.INVENTORY_CLICK;
    private static final long ACK_TIMEOUT_NANOS = 5_000_000_000L;
    private static final List<Move> withdrawn = new ArrayList<>();
    private static final List<Move> plan = new ArrayList<>();
    private static final List<ItemStack> expected = new ArrayList<>();
    private static DispenserScreen screen;
    private static DispenserMenu handler;
    private static ClientLevel world;
    private static Phase phase = Phase.IDLE;
    private static boolean restoring;
    private static boolean slotConfirmed;
    private static boolean cursorConfirmed;
    private static boolean registered;
    private static int moveIndex;
    private static int awaitedSlot = -1;
    private static long sentAt;
    private static long readyTick;
    private static ItemStack beforeCursor = ItemStack.EMPTY;
    private static ItemStack afterCursor = ItemStack.EMPTY;
    private static ItemStack afterSlot = ItemStack.EMPTY;
    private static String status = "Open a dispenser or dropper";

    private DispenserHelper() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        DispenserConfig c = config.dispenser;
        return List.of(GuiModule.toggle("Dispenser Selector",
                "Reference: Gamble Rigger. Keeps one dispenser slot by withdrawing other stacks into empty inventory slots.",
                () -> c.enabled, value -> { c.enabled = value; if (!value) reset(client); })
            .with(new GuiSetting.Slider("Keep slot", () -> c.keepSlot, value -> c.keepSlot = value, 1, 9, ""))
            .with(new GuiSetting.Info("Use", () -> "Buttons in the open dispenser"))
            .with(new GuiSetting.Info("Rules", () -> "Normal inventory moves; no odds bypass"))
            .with(new GuiSetting.Info("Status", () -> status)).build());
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            ClientCommands.literal("arcanedispenser")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("Open a dispenser/dropper and use Keep, Withdraw or Restore. Slots 1-9 run left-to-right, top-to-bottom. " + status));
                    return 1;
                })
                .then(ClientCommands.literal("keep").then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 9))
                    .executes(ctx -> keep(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "slot")) ? 1 : 0)))
                .then(ClientCommands.literal("restore").executes(ctx -> restore(Minecraft.getInstance()) ? 1 : 0))
                .then(ClientCommands.literal("stop").executes(ctx -> {
                    abort(Minecraft.getInstance(), "Stopped; check your cursor before moving items"); return 1;
                }))));
        ScreenEvents.AFTER_INIT.register((client, current, width, height) -> {
            if (!(current instanceof DispenserScreen container) || !enabled()) return;
            int left = (width - 176) / 2;
            int top = (height - 166) / 2;
            int buttonsY = Math.max(2, top - 23);
            Button keep = Button.builder(Component.literal("Keep: " + settings().keepSlot), button -> {
                settings().keepSlot = settings().keepSlot % 9 + 1;
                ArcaneClient.config().save();
                button.setMessage(Component.literal("Keep: " + settings().keepSlot));
            }).bounds(left, buttonsY, 52, 20).build();
            keep.setTooltip(Tooltip.create(Component.literal("Choose slot 1-9: left-to-right, top-to-bottom. The chosen slot stays in the container.")));
            Button withdraw = Button.builder(Component.literal("Withdraw"), button -> {
                if (busy()) abort(client, "Stopped; check your cursor before moving items");
                else keep(client, settings().keepSlot);
            }).bounds(left + 54, buttonsY, 66, 20).build();
            withdraw.setTooltip(Tooltip.create(Component.literal("Move every other occupied slot into separate empty inventory slots. Click Stop to interrupt.")));
            Button restore = Button.builder(Component.literal("Restore"), button -> restore(client))
                .bounds(left + 122, buttonsY, 54, 20).build();
            restore.setTooltip(Tooltip.create(Component.literal("Return only unchanged withdrawn stacks during this same open container session.")));
            Screens.getWidgets(container).add(keep);
            Screens.getWidgets(container).add(withdraw);
            Screens.getWidgets(container).add(restore);
            ScreenEvents.beforeExtract(container).register((currentScreen, draw, mouseX, mouseY, delta) -> {
                keep.active = enabled() && !busy();
                withdraw.active = enabled();
                withdraw.setMessage(Component.literal(busy() ? "Stop" : "Withdraw"));
                restore.active = enabled() && !busy() && handler == container.getMenu() && !withdrawn.isEmpty();
            });
            ScreenEvents.afterForeground(container).register((currentScreen, draw, mouseX, mouseY, delta) -> {
                if (!enabled()) return;
                int textY = Math.min(height - 12, top + 170);
                String line = client.font.plainSubstrByWidth(status, Math.min(width - 8, 320));
                draw.text(client.font, line, (width - client.font.width(line)) / 2, textY, 0xFFFFD98C);
            });
            ScreenEvents.remove(container).register(removed -> {
                if (screen == container) reset(client);
            });
        });
    }

    private static boolean enabled() { return ArcaneClient.config() != null && settings().enabled; }
    private static DispenserConfig settings() { return ArcaneClient.config().dispenser; }
    private static boolean busy() { return phase != Phase.IDLE; }

    private static boolean usable(Minecraft client) {
        return enabled() && client.player != null && client.level != null && client.gameMode != null
            && client.getConnection() != null && client.player.isAlive() && !client.player.isSpectator()
            && !client.player.isCreative() && !client.player.isUsingItem() && !client.isPaused()
            && !DetachedCameraInteraction.isActive() && client.gui.screen() instanceof DispenserScreen container
            && container.getMenu() == client.player.containerMenu
            && client.player.containerMenu.getClass() == DispenserMenu.class
            && client.player.containerMenu.slots.size() == 45;
    }

    private static boolean sameSession(Minecraft client) {
        return usable(client) && client.level == world && client.gui.screen() == screen
            && client.player.containerMenu == handler;
    }

    public static boolean keep(Minecraft client, int keepSlot) {
        if (!usable(client)) return report(client, "Enable Dispenser Selector and open a dispenser/dropper");
        if (busy()) return report(client, "Wait for the current transfer, or press Stop");
        if (!sameSession(client)) reset(client);
        if (!withdrawn.isEmpty()) return report(client, "Restore the previous withdrawal or close this container first");
        var current = (DispenserMenu)client.player.containerMenu;
        if (!current.getCarried().isEmpty()) return report(client, "Empty your cursor before withdrawing items");
        int keep = Math.clamp(keepSlot, 1, 9) - 1;
        if (current.getSlot(keep).getItem().isEmpty()) return report(client, "Choose an occupied slot to keep");
        List<Slot> empty = new ArrayList<>();
        for (Slot slot : current.slots) {
            if (slot.container == client.player.getInventory() && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36
                && slot.isActive() && slot.getItem().isEmpty()) empty.add(slot);
        }
        List<Move> moves = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            Slot source = current.getSlot(index);
            if (index == keep || source.getItem().isEmpty()) continue;
            if (!source.isActive() || !source.mayPickup(client.player)) return report(client, "A container slot cannot be taken");
            Slot destination = null;
            for (Slot candidate : empty) {
                if (candidate.mayPlace(source.getItem()) && candidate.getMaxStackSize(source.getItem()) >= source.getItem().getCount()) {
                    destination = candidate;
                    break;
                }
            }
            if (destination == null) return report(client, "Not enough separate empty inventory slots; nothing moved");
            empty.remove(destination);
            moves.add(new Move(source.index, destination.index, source.getItem().copy()));
        }
        if (moves.isEmpty()) return report(client, "Only the selected slot is occupied");
        return start(client, current, moves, false);
    }

    public static boolean restore(Minecraft client) {
        if (!sameSession(client)) { reset(client); return report(client, "Restore is available only in the same open container"); }
        if (busy()) return report(client, "Wait for the current transfer, or press Stop");
        if (!handler.getCarried().isEmpty()) return report(client, "Empty your cursor before restoring items");
        if (withdrawn.isEmpty()) return report(client, "There are no withdrawn stacks to restore");
        List<Move> moves = new ArrayList<>();
        for (int i = withdrawn.size() - 1; i >= 0; i--) {
            Move move = withdrawn.get(i);
            Slot source = handler.getSlot(move.inventorySlot);
            Slot destination = handler.getSlot(move.containerSlot);
            if (!ItemStack.matches(source.getItem(), move.stack) || !destination.getItem().isEmpty()
                || !source.mayPickup(client.player) || !destination.mayPlace(move.stack)
                || !source.isActive() || !destination.isActive() || destination.getMaxStackSize(move.stack) < move.stack.getCount()) {
                return report(client, "Items or original slots changed; restore cancelled without moving anything");
            }
            moves.add(move);
        }
        return start(client, handler, moves, true);
    }

    private static boolean start(Minecraft client, DispenserMenu current, List<Move> moves, boolean restore) {
        long tick = InventoryAutomationSupport.tick(client.player);
        if (!InventoryActionScheduler.shared().tryAcquire(OWNER, CHANNEL, tick, 4)) return report(client, "Another inventory action is active");
        screen = (DispenserScreen)client.gui.screen();
        handler = current;
        world = client.level;
        plan.clear();
        plan.addAll(moves);
        expected.clear();
        for (Slot slot : current.slots) expected.add(slot.getItem().copy());
        restoring = restore;
        moveIndex = 0;
        phase = Phase.READY;
        readyTick = tick + 1;
        status = restore ? "Restoring; wait for each server update" : "Withdrawing; wait for each server update";
        return true;
    }

    public static void tick(Minecraft client) {
        if (handler != null && !sameSession(client)) { reset(client); return; }
        if (!busy()) return;
        long tick = InventoryAutomationSupport.tick(client.player);
        InventoryActionScheduler scheduler = InventoryActionScheduler.shared();
        if (!scheduler.isOwnedBy(OWNER, CHANNEL, tick) || !scheduler.tryAcquire(OWNER, CHANNEL, tick, 4)) {
            abort(client, "Stopped: another inventory action took priority; check cursor"); return;
        }
        if (!unchangedExceptPending()) { abort(client, "Stopped: inventory or cursor changed; check cursor"); return; }
        if (phase == Phase.READY) {
            if (tick < readyTick) return;
            Move move = plan.get(moveIndex);
            Slot source = handler.getSlot(source(move));
            Slot destination = handler.getSlot(destination(move));
            if (!ItemStack.matches(source.getItem(), move.stack) || !destination.getItem().isEmpty()
                || !source.mayPickup(client.player) || !destination.mayPlace(move.stack)
                || destination.getMaxStackSize(move.stack) < move.stack.getCount()) {
                abort(client, "Stopped: transfer slots changed"); return;
            }
            sendClick(client, source.index, ItemStack.EMPTY, move.stack, Phase.PICKUP);
            return;
        }
        if (System.nanoTime() - sentAt > ACK_TIMEOUT_NANOS) {
            abort(client, "Server update timed out; check cursor and finish manually"); return;
        }
        if (!slotConfirmed || !cursorConfirmed) return;
        if (!ItemStack.matches(handler.getSlot(awaitedSlot).getItem(), afterSlot)
            || !ItemStack.matches(handler.getCarried(), afterCursor)) {
            abort(client, "Stopped: server contents differ; check cursor"); return;
        }
        expected.set(awaitedSlot, afterSlot.copy());
        Move move = plan.get(moveIndex);
        if (phase == Phase.PICKUP) {
            Slot destination = handler.getSlot(destination(move));
            if (!destination.getItem().isEmpty() || !destination.mayPlace(move.stack)
                || destination.getMaxStackSize(move.stack) < move.stack.getCount()) {
                abort(client, "Destination changed; place the cursor stack manually"); return;
            }
            sendClick(client, destination.index, move.stack, ItemStack.EMPTY, Phase.PLACE);
        } else {
            if (restoring) withdrawn.remove(move); else withdrawn.add(move);
            moveIndex++;
            if (moveIndex == plan.size()) {
                boolean wasRestore = restoring;
                clearOperation();
                report(client, wasRestore ? "Restored withdrawn stacks" : "Withdrawal complete; Restore is available until this screen closes");
            } else {
                phase = Phase.READY;
                awaitedSlot = -1;
                readyTick = tick + 2;
                status = (restoring ? "Restoring " : "Withdrawing ") + moveIndex + "/" + plan.size();
            }
        }
    }

    private static int source(Move move) { return restoring ? move.inventorySlot : move.containerSlot; }
    private static int destination(Move move) { return restoring ? move.containerSlot : move.inventorySlot; }

    private static boolean unchangedExceptPending() {
        if (handler.slots.size() != expected.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            ItemStack actual = handler.getSlot(index).getItem();
            if (ItemStack.matches(actual, expected.get(index))) continue;
            if (phase != Phase.READY && index == awaitedSlot && ItemStack.matches(actual, afterSlot)) continue;
            return false;
        }
        ItemStack cursor = handler.getCarried();
        if (phase == Phase.READY) return cursor.isEmpty();
        return ItemStack.matches(cursor, beforeCursor) || ItemStack.matches(cursor, afterCursor);
    }

    private static void sendClick(Minecraft client, int slot, ItemStack slotResult, ItemStack cursorResult, Phase nextPhase) {
        beforeCursor = handler.getCarried().copy();
        afterCursor = cursorResult.copy();
        afterSlot = slotResult.copy();
        awaitedSlot = slot;
        slotConfirmed = cursorConfirmed = false;
        sentAt = System.nanoTime();
        phase = nextPhase;
        // No optimistic local click. Vanilla's prediction map is optional: keeping it empty and
        // reporting our CURRENT cursor hash makes the server send authoritative changed slots
        // and cursor contents. Correct predicted clickSlot calls otherwise have no success ACK.
        client.getConnection().send(new ServerboundContainerClickPacket(handler.containerId, handler.getStateId(),
            (short)slot, (byte)0, ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(),
            HashedStack.create(beforeCursor, client.getConnection().decoratedHashOpsGenenerator())));
    }

    public static void onSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        if (!busy() || phase == Phase.READY || handler == null || packet.getContainerId() != handler.containerId) return;
        if (packet.getSlot() == awaitedSlot && ItemStack.matches(packet.getItem(), afterSlot)) slotConfirmed = true;
    }

    public static void onCursorUpdate(ItemStack stack) {
        if (busy() && phase != Phase.READY && ItemStack.matches(stack, afterCursor)) cursorConfirmed = true;
    }

    public static void onInventory(ClientboundContainerSetContentPacket packet) {
        if (!busy() || phase == Phase.READY || handler == null || packet.containerId() != handler.containerId) return;
        if (awaitedSlot >= 0 && awaitedSlot < packet.items().size()
            && ItemStack.matches(packet.items().get(awaitedSlot), afterSlot)) slotConfirmed = true;
        if (ItemStack.matches(packet.carriedItem(), afterCursor)) cursorConfirmed = true;
    }

    /** Cancels the first interfering click so it cannot race a server-owned cursor stack. */
    public static boolean interruptClick(Minecraft client, int syncId) {
        if (!busy() || handler == null || syncId != handler.containerId) return false;
        abort(client, "Selector stopped by another click; check cursor, then click again");
        return true;
    }

    private static void abort(Minecraft client, String message) {
        clearOperation();
        report(client, message);
    }

    private static void clearOperation() {
        phase = Phase.IDLE;
        plan.clear();
        expected.clear();
        awaitedSlot = -1;
        slotConfirmed = cursorConfirmed = false;
        beforeCursor = afterCursor = afterSlot = ItemStack.EMPTY;
        InventoryActionScheduler.shared().releaseAll(OWNER);
    }

    public static void reset(Minecraft client) {
        clearOperation();
        withdrawn.clear();
        screen = null;
        handler = null;
        world = null;
        status = "Open a dispenser or dropper";
    }

    private static boolean report(Minecraft client, String message) {
        status = message;
        if (client.player != null) client.player.sendSystemMessage(Component.literal("Dispenser Selector: " + message));
        return false;
    }
}
