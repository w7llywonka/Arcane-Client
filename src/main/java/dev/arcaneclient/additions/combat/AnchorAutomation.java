package dev.arcaneclient.additions.combat;

import dev.arcaneclient.inventory.InventoryActionScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** One charge and one detonation attempt per aimed anchor; never retries a stalled step. */
final class AnchorAutomation {
    private enum Phase { SEEKING, CHARGE_SENT, DETONATE_SENT, FINISHED }

    private static Phase phase = Phase.SEEKING;
    private static BlockPos activeAnchor;
    private static BlockPos lastAnchor;
    private static int completed;
    private static long stepTick;
    private static long nextStepTick;
    private static int holdSlot = -1;

    private AnchorAutomation() { }

    static boolean ownsHold() {
        return activeAnchor != null || completed > 0 || phase == Phase.FINISHED;
    }

    static void reset() {
        phase = Phase.SEEKING;
        activeAnchor = lastAnchor = null;
        completed = 0;
        stepTick = nextStepTick = 0;
        holdSlot = -1;
    }

    static void tick(Minecraft client, CombatAdditionsConfig config, boolean held) {
        if ((!config.anchorMacro && !config.doubleAnchor) || !held) {
            reset();
            return;
        }
        if (client.player.isShiftKeyDown() || phase == Phase.FINISHED) return;
        long tick = client.player.tickCount;
        if (holdSlot >= 0 && client.player.getInventory().getSelectedSlot() != holdSlot) {
            phase = Phase.FINISHED;
            return;
        }
        if (phase == Phase.DETONATE_SENT) {
            // A second cycle needs the first anchor to disappear and a different aimed anchor.
            if (!client.level.getBlockState(activeAnchor).is(Blocks.RESPAWN_ANCHOR)) {
                lastAnchor = activeAnchor;
                activeAnchor = null;
                completed++;
                phase = completed >= (config.doubleAnchor ? 2 : 1) ? Phase.FINISHED : Phase.SEEKING;
            } else if (tick - stepTick > 40) phase = Phase.FINISHED;
            return;
        }
        if (phase == Phase.CHARGE_SENT && tick - stepTick > 40) {
            phase = Phase.FINISHED;
            return;
        }
        if (tick < nextStepTick || !(client.hitResult instanceof BlockHitResult hit)
            || !CombatAdditionsController.usableBlockHit(client, hit)) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)
            || pos.equals(lastAnchor)
            || activeAnchor != null && !activeAnchor.equals(pos)
            || client.level.dimension().equals(Level.NETHER)
            || client.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos)
            || !CombatAdditionsController.explosionGate(client, Vec3.atCenterOf(pos),
                config.anchorMinimumHealth, config.anchorMinimumDistance)) return;
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        if (phase == Phase.CHARGE_SENT && charges == 0) return;
        if (charges == 0) {
            int glowstone = CombatAdditionsController.findHotbar(client, stack -> stack.is(Items.GLOWSTONE));
            int original = client.player.getInventory().getSelectedSlot();
            CombatAdditionsController.withSlot(client, InventoryActionScheduler.Owner.ANCHOR_ACTION, glowstone, () -> {
                holdSlot = original;
                activeAnchor = pos.immutable();
                stepTick = tick;
                nextStepTick = tick + config.anchorDelayTicks;
                phase = Phase.CHARGE_SENT;
                boolean accepted = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit).consumesAction();
                if (accepted) client.player.swing(InteractionHand.MAIN_HAND,
                    client.player.getMainHandItem().getInteractAnimation(), false);
                else phase = Phase.FINISHED;
                return accepted;
            });
        } else {
            // Vanilla defers to offhand glowstone when the main hand is not glowstone.
            if (client.player.getOffhandItem().is(Items.GLOWSTONE)) return;
            int neutral = CombatAdditionsController.findHotbar(client,
                stack -> !stack.is(Items.GLOWSTONE) && !stack.is(Items.RESPAWN_ANCHOR));
            int original = client.player.getInventory().getSelectedSlot();
            CombatAdditionsController.withSlot(client, InventoryActionScheduler.Owner.ANCHOR_ACTION, neutral, () -> {
                holdSlot = original;
                activeAnchor = pos.immutable();
                stepTick = tick;
                nextStepTick = tick + config.anchorDelayTicks;
                phase = Phase.DETONATE_SENT;
                boolean accepted = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit).consumesAction();
                if (accepted) client.player.swing(InteractionHand.MAIN_HAND,
                    client.player.getMainHandItem().getInteractAnimation(), false);
                else phase = Phase.FINISHED;
                return accepted;
            });
        }
    }
}
