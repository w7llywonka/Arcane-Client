package dev.arcaneclient.additions.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded interactions with crystals that actually exist in the client world. */
final class CrystalAutomation {
    private static final Map<Integer, Long> recentlyAttacked = new HashMap<>();
    private static long nextActionTick;
    private static BlockPos lastPlacement;
    private static long nextPlacementTick;

    private CrystalAutomation() { }

    static void reset() {
        recentlyAttacked.clear();
        nextActionTick = nextPlacementTick = 0;
        lastPlacement = null;
    }

    static void tick(Minecraft client, CombatAdditionsConfig config, boolean attackHeld, boolean useHeld) {
        if (!config.autoCrystal) {
            reset();
            return;
        }
        long tick = client.player.tickCount;
        recentlyAttacked.entrySet().removeIf(entry -> entry.getValue() <= tick);
        if (tick < nextActionTick || client.player.isShiftKeyDown()) return;
        if (attackHeld) breakCrystal(client, config, tick);
        else if (useHeld && !AnchorAutomation.ownsHold()) placeCrystal(client, config, tick);
    }

    private static void breakCrystal(Minecraft client, CombatAdditionsConfig config, long tick) {
        EndCrystal best = null;
        double bestDistance = Double.MAX_VALUE;
        double range = Math.min(6.0,
            client.player.getAttackRangeWith(client.player.getMainHandItem()).effectiveMaxRange(client.player));
        for (EndCrystal crystal : client.level.getEntitiesOfClass(EndCrystal.class,
            client.player.getBoundingBox().inflate(range + 1.0), entity -> true)) {
            if (!crystal.isAlive() || crystal.isRemoved() || !crystal.isAttackable()
                || client.level.getEntity(crystal.getId()) != crystal
                || recentlyAttacked.containsKey(crystal.getId())
                || !client.player.isWithinAttackRange(client.player.getMainHandItem(), crystal.getBoundingBox(), 0.0)
                || !client.player.hasLineOfSight(crystal)
                || !CombatAdditionsController.clearRay(client, crystal.getBoundingBox().getCenter())
                || !CombatAdditionsController.explosionGate(client, crystal.position(),
                    config.crystalMinimumHealth, config.crystalMinimumDistance)) continue;
            double distance = crystal.distanceToSqr(client.player);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = crystal;
        }
        if (best == null || !CombatAdditionsController.acquire(client, InventoryActionScheduler.Owner.CRYSTAL_ACTION)) return;
        try {
            client.gameMode.attack(client.player, best);
            client.player.swing(InteractionHand.MAIN_HAND,
                client.player.getMainHandItem().getAttackAnimation(), false);
            // Never retry the same entity every tick while waiting for its removal packet.
            recentlyAttacked.put(best.getId(), tick + Math.max(20, config.crystalDelayTicks));
            nextActionTick = tick + config.crystalDelayTicks;
        } finally {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.CRYSTAL_ACTION);
        }
    }

    private static void placeCrystal(Minecraft client, CombatAdditionsConfig config, long tick) {
        if (!(client.hitResult instanceof BlockHitResult hit)
            || !CombatAdditionsController.usableBlockHit(client, hit)) return;
        BlockPos base = hit.getBlockPos();
        BlockState state = client.level.getBlockState(base);
        if (!(state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK))
            || base.equals(lastPlacement) && tick < nextPlacementTick
            || !client.level.getBlockState(base.above()).isAir()
            || !client.level.getBlockState(base.above(2)).isAir()) return;
        Vec3 center = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        if (!CombatAdditionsController.explosionGate(client, center,
            config.crystalMinimumHealth, config.crystalMinimumDistance)) return;
        AABB occupancy = new AABB(base.getX(), base.getY() + 1, base.getZ(),
            base.getX() + 1, base.getY() + 3, base.getZ() + 1);
        if (!client.level.getEntities(null, occupancy).isEmpty()) return;
        int slot = CombatAdditionsController.findHotbar(client, stack -> stack.is(Items.END_CRYSTAL));
        CombatAdditionsController.withSlot(client, InventoryActionScheduler.Owner.CRYSTAL_ACTION, slot, () -> {
            // Back off after an attempt even if the server rejects placement.
            nextActionTick = tick + config.crystalDelayTicks;
            lastPlacement = base.immutable();
            nextPlacementTick = tick + Math.max(20, config.crystalDelayTicks);
            boolean accepted = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit).consumesAction();
            if (accepted) client.player.swing(InteractionHand.MAIN_HAND,
                client.player.getMainHandItem().getInteractAnimation(), false);
            return accepted;
        });
    }
}
