package dev.arcaneclient.additions.nuker;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.screen.BlockEspPickerScreen;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import dev.arcaneclient.utility.AutoToolController;
import dev.arcaneclient.utility.QualityOfLifeController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Serial vanilla mining, never local-only block deletion or a burst of break packets. */
public final class Nuker {
    private static final List<BlockPos> OFFSETS = offsets();
    private static ClientLevel world;
    private static BlockPos target;
    private static boolean ownsMining;
    private static int cursor, lastStep = -1, readyAt, targetSince;
    private Nuker() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        var c = config.nuker;
        return List.of(GuiModule.toggle("Nuker", "Mines nearby visible blocks one at a time at normal survival speed. Uses Auto Tool when enabled.",
            () -> c.enabled, value -> { c.enabled = value; reset(client); })
            .with(new GuiSetting.Slider("Range", () -> c.range, v -> c.range = v, 1, 6, "m"))
            .with(new GuiSetting.Slider("Between blocks", () -> c.delayTicks, v -> c.delayTicks = v, 1, 20, " ticks"))
            .with(new GuiSetting.Toggle("Hold attack to mine", () -> c.holdAttack, v -> c.holdAttack = v))
            .with(new GuiSetting.Toggle("Keep blocks below feet", () -> c.protectFloor, v -> c.protectFloor = v))
            .with(new GuiSetting.Toggle("Protect block entities", () -> c.protectBlockEntities, v -> c.protectBlockEntities = v))
            .with(new GuiSetting.Cycle("Block filter", () -> c.allBlocks ? "All eligible blocks" : c.blocks.size() + " selected",
                () -> client.gui.setScreen(new BlockEspPickerScreen(client.gui.screen(), config, "NUKER",
                    () -> c.blocks, () -> c.allBlocks, v -> c.allBlocks = v,
                    () -> "Only reachable, visible blocks are mined. Floor/container protection still applies."))))
            .with(new GuiSetting.Info("Camera modes", () -> "Paused in Freecam / Freelook"))
            .with(new GuiSetting.Info("Reach", () -> "Capped by player interaction range"))
            .build());
    }

    public static boolean shouldHandle(Minecraft client) {
        var config = ArcaneClient.config();
        return config != null && config.nuker.enabled && client.player != null && client.level != null
            && client.gameMode != null && client.gui.screen() == null && !client.isPaused()
            && client.player.isAlive() && !client.player.isSpectator() && !client.player.isUsingItem()
            && !client.player.blockActionRestricted(client.level, client.player.blockPosition(), client.gameMode.getPlayerMode())
            && !client.options.keyUse.isDown() && !DetachedCameraInteraction.isActive()
            && (!config.nuker.holdAttack || client.options.keyAttack.isDown());
    }

    /** Called from vanilla's mining input boundary; returning true replaces only that mining path. */
    public static boolean handleMining(Minecraft client) {
        if (!shouldHandle(client)) { reset(client); return false; }
        if (world != client.level) { reset(client); world = client.level; }
        int tick = client.player.tickCount;
        if (lastStep == tick) return true;
        lastStep = tick;
        var c = ArcaneClient.config().nuker;
        if (target != null && (client.level.getBlockState(target).isAir() || tick - targetSince > 600)) {
            stopTarget(client);
            readyAt = tick + Math.clamp(c.delayTicks, 1, 20);
        }
        if (tick < readyAt) return true;
        BlockHitResult hit = target == null ? findTarget(client, c) : visibleHit(client, target, c);
        if (hit == null) { stopTarget(client); AutoToolController.restore(client); return true; }
        if (!InventoryActionScheduler.shared().tryAcquire(InventoryActionScheduler.Owner.AUTO_TOOL,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION, tick, 1)) { stopTarget(client); return true; }
        ownsMining = true;
        if (ArcaneClient.config().autoTool) AutoToolController.selectForState(client, client.player,
            client.level.getBlockState(hit.getBlockPos()), ArcaneClient.config().autoToolPreserveDurability);
        else AutoToolController.restore(client);
        if (QualityOfLifeController.shouldProtect(client.player.getMainHandItem())) { reset(client); return true; }
        if (target == null) { target = hit.getBlockPos().immutable(); targetSince = tick; }
        if (client.gameMode.continueDestroyBlock(target, hit.getDirection()))
            client.player.swing(InteractionHand.MAIN_HAND, client.player.getMainHandItem().getAttackAnimation(), false);
        return true;
    }

    /** Screens can bypass vanilla's mining method, so clean up on the ordinary tick as well. */
    public static void tick(Minecraft client) {
        if (world != client.level || !shouldHandle(client)) reset(client);
    }

    public static void reset(Minecraft client) {
        if (ownsMining) {
            stopTarget(client);
            AutoToolController.restore(client);
        }
        ownsMining = false;
        world = null; target = null; cursor = 0; lastStep = -1; readyAt = targetSince = 0;
    }

    public static BlockPos currentTarget() { return target; }

    private static void stopTarget(Minecraft client) {
        if (ownsMining && client.level == world && client.gameMode != null)
            client.gameMode.stopDestroyBlock();
        target = null;
        // Keep ownership until reset so the Auto Tool slot is restored after a gap or disable.
    }

    private static BlockHitResult findTarget(Minecraft client, NukerConfig c) {
        BlockPos base = client.player.blockPosition();
        int rays = 0;
        for (int checked = 0; checked < 128; checked++) {
            BlockPos pos = base.offset(OFFSETS.get(cursor));
            cursor = (cursor + 1) % OFFSETS.size();
            if (!eligible(client, pos, c)) continue;
            BlockHitResult hit = ray(client, pos);
            if (hit != null) return hit;
            if (++rays == 16) break;
        }
        return null;
    }

    private static boolean eligible(Minecraft client, BlockPos pos, NukerConfig c) {
        double reach = Math.min(Math.clamp(c.range, 1, 6), client.player.blockInteractionRange());
            if (client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach
            || !client.level.getWorldBorder().isWithinBounds(pos)
            || client.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false) == null
            || !client.player.isWithinBlockInteractionRange(pos, 0)) return false;
        var state = client.level.getBlockState(pos);
        return !state.isAir() && !state.liquid() && state.getDestroySpeed(client.level, pos) >= 0
            && c.accepts(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), pos.getY(), client.player.getBlockY(), state.hasBlockEntity());
    }

    private static BlockHitResult visibleHit(Minecraft client, BlockPos pos, NukerConfig c) {
        return eligible(client, pos, c) ? ray(client, pos) : null;
    }

    private static BlockHitResult ray(Minecraft client, BlockPos pos) {
            var hit = client.level.clip(new ClipContext(client.player.getEyePosition(), Vec3.atCenterOf(pos),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }

    private static List<BlockPos> offsets() {
        var result = new ArrayList<BlockPos>();
        for (int x = -6; x <= 6; x++) for (int y = -6; y <= 7; y++) for (int z = -6; z <= 6; z++)
            result.add(new BlockPos(x, y, z));
        result.sort(Comparator.comparingDouble(p -> p.getX() * p.getX() + (p.getY() - 1) * (p.getY() - 1) + p.getZ() * p.getZ()));
        return List.copyOf(result);
    }
}
