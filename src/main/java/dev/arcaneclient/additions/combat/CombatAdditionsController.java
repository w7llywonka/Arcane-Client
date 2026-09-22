package dev.arcaneclient.additions.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.DetachedCameraInteraction;
import dev.arcaneclient.inventory.InventoryActionScheduler;
import dev.arcaneclient.inventory.InventoryAutomationSupport;
import dev.arcaneclient.mixin.KeyBindingAccessor;
import dev.arcaneclient.mixin.additions.CombatClientInvoker;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Input-bound combat helpers; no synthetic entities, reach extension, or packet bursts. */
public final class CombatAdditionsController {
    private static ClientLevel trackedWorld;
    private static LocalPlayer trackedPlayer;
    private static long nextClickNanos;
    private static boolean invokingAttack;

    private CombatAdditionsController() { }

    public static void tick(Minecraft client) {
        if (trackedWorld != client.level || trackedPlayer != client.player) {
            reset(client);
            trackedWorld = client.level;
            trackedPlayer = client.player;
        }
        CombatAdditionsConfig settings = settings();
        if (settings == null || !ready(client)) {
            nextClickNanos = 0;
            CrystalAutomation.reset();
            AnchorAutomation.reset();
            MaceBomberController.suspend(client);
            return;
        }
        boolean attackHeld = physical(client, client.options.keyAttack);
        boolean useHeld = physical(client, client.options.keyUse);
        if (settings.aimAssist && attackHeld && !useHeld) aim(client, settings);
        MaceBomberController.tick(client, settings, attackHeld, useHeld);
        click(client, settings, attackHeld && !useHeld && !MaceBomberController.controlsAttack(client, settings));
        CrystalAutomation.tick(client, settings, attackHeld && !useHeld, useHeld);
        AnchorAutomation.tick(client, settings, useHeld);
    }

    public static void reset(Minecraft client) {
        nextClickNanos = 0;
        invokingAttack = false;
        trackedWorld = null;
        trackedPlayer = null;
        CrystalAutomation.reset();
        AnchorAutomation.reset();
        ShieldBreakerController.restore(client);
        MaceBomberController.reset(client);
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AIM_ACTION);
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.CRYSTAL_ACTION);
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.ANCHOR_ACTION);
    }

    static CombatAdditionsConfig settings() {
        ArcaneConfig config = ArcaneClient.config();
        return config == null ? null : config.combatAdditions;
    }

    static boolean ready(Minecraft client) {
        return client != null && client.player != null && client.level != null
            && client.gameMode != null && client.gui.screen() == null
            && client.isWindowActive() && !client.isPaused() && !DetachedCameraInteraction.isActive()
            && client.player.isAlive() && !client.player.isSpectator()
            && !client.player.isUsingItem()
            && client.player.containerMenu == client.player.inventoryMenu
            && client.player.inventoryMenu.getCarried().isEmpty();
    }

    static boolean validLivingTarget(Minecraft client, LivingEntity target) {
        return target != client.player && target.isAlive() && !target.isRemoved() && target.isAttackable()
            && client.level.getEntity(target.getId()) == target
            && !client.player.isAlliedTo(target)
            && !(target instanceof Player player && (player.isSpectator() || player.isCreative()))
            && client.player.hasLineOfSight(target);
    }

    static boolean physical(Minecraft client, KeyMapping binding) {
        InputConstants.Key key = ((KeyBindingAccessor)(Object)binding).arcaneclient$boundKey();
        if (key.getType() == InputConstants.Type.KEYBOARD) return InputConstants.isKeyDown(key.getValue());
        if (key.getType() == InputConstants.Type.MOUSE) {
            return switch (key.getValue()) {
                case InputConstants.MOUSE_BUTTON_LEFT -> client.mouseHandler.isLeftPressed();
                case InputConstants.MOUSE_BUTTON_MIDDLE -> client.mouseHandler.isMiddlePressed();
                case InputConstants.MOUSE_BUTTON_RIGHT -> client.mouseHandler.isRightPressed();
                default -> binding.isDown();
            };
        }
        return false;
    }

    public static boolean controlsAttack(Minecraft client) {
        CombatAdditionsConfig config = settings();
        if (invokingAttack || config == null || !ready(client)) return false;
        return MaceBomberController.controlsAttack(client, config)
            || config.autoClicker && physical(client, client.options.keyAttack)
                && !physical(client, client.options.keyUse) && clickTarget(client) != null;
    }

    public static boolean controlsUse(Minecraft client) {
        CombatAdditionsConfig config = settings();
        if (config == null || !ready(client) || !physical(client, client.options.keyUse)) return false;
        if ((config.anchorMacro || config.doubleAnchor) && AnchorAutomation.ownsHold()) return true;
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        var state = client.level.getBlockState(hit.getBlockPos());
        return ((config.anchorMacro || config.doubleAnchor) && state.is(Blocks.RESPAWN_ANCHOR))
            || (config.autoCrystal && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)));
    }

    static LivingEntity clickTarget(Minecraft client) {
        if (!(client.hitResult instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof LivingEntity living)
            || !validLivingTarget(client, living)
            || !client.player.isWithinAttackRange(client.player.getMainHandItem(), living.getBoundingBox(), 0.0)) return null;
        return living;
    }

    private static void click(Minecraft client, CombatAdditionsConfig config, boolean held) {
        if (!config.autoClicker || !held || clickTarget(client) == null) {
            nextClickNanos = 0;
            return;
        }
        if (!config.clickerIgnoreCooldown && client.player.getAttackStrengthScale(0.0f) < 0.95f) return;
        long now = System.nanoTime();
        if (nextClickNanos != 0 && now < nextClickNanos) return;
        if (!acquire(client, InventoryActionScheduler.Owner.AIM_ACTION)) return;
        // Existing manual preparation may select a higher-priority weapon during vanilla doAttack.
        InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AIM_ACTION);
        invokingAttack = true;
        try {
            ((CombatClientInvoker)(Object)client).arcaneclient$invokeCombatAttack();
        } finally {
            invokingAttack = false;
            // Schedule from now: a stalled client never catches up with a burst of clicks.
            nextClickNanos = now + 1_000_000_000L / Math.clamp(config.clickerCps, 1, 12);
        }
    }

    private static void aim(Minecraft client, CombatAdditionsConfig config) {
        LocalPlayer player = client.player;
        Vec3 eye = player.getEyePosition();
        double bestAngle = Math.clamp(config.aimFov, 10, 120) / 2.0;
        float selectedYaw = player.getYRot();
        float selectedPitch = player.getXRot();
        boolean found = false;
        for (Player target : client.level.players()) {
            if (!validLivingTarget(client, target)) continue;
            Vec3 point = target.getBoundingBox().getCenter();
            Vec3 delta = point.subtract(eye);
            double range = Math.clamp(config.aimRange, 1, 6);
            if (delta.lengthSqr() > range * range || !clearRay(client, point)) continue;
            float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
            float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
            double angle = Math.hypot(Mth.wrapDegrees(yaw - player.getYRot()), pitch - player.getXRot());
            if (angle >= bestAngle) continue;
            bestAngle = angle;
            selectedYaw = yaw;
            selectedPitch = pitch;
            found = true;
        }
        if (!found || !acquire(client, InventoryActionScheduler.Owner.AIM_ACTION)) return;
        try {
            float yawDelta = Mth.wrapDegrees(selectedYaw - player.getYRot());
            float pitchDelta = selectedPitch - player.getXRot();
            double length = Math.hypot(yawDelta, pitchDelta);
            double step = Math.min(Math.clamp(config.aimDegreesPerTick, 1, 10), length * 0.25);
            if (length > 0.001) {
                player.setYRot(player.getYRot() + (float)(yawDelta * step / length));
                player.setXRot(Mth.clamp(player.getXRot() + (float)(pitchDelta * step / length), -90.0f, 90.0f));
                // Minecraft 26.3 refreshes the private crosshair pick during the next frame.
            }
        } finally {
            InventoryActionScheduler.shared().releaseAll(InventoryActionScheduler.Owner.AIM_ACTION);
        }
    }

    static boolean clearRay(Minecraft client, Vec3 point) {
        return client.level.clip(new ClipContext(client.player.getEyePosition(), point,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player)).getType() == HitResult.Type.MISS;
    }

    static boolean usableBlockHit(Minecraft client, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK
            || !client.player.isWithinBlockInteractionRange(hit.getBlockPos(), 0.0)
            || client.player.getEyePosition().distanceToSqr(hit.getLocation())
                > client.player.blockInteractionRange() * client.player.blockInteractionRange()) return false;
        Vec3 beyond = hit.getLocation().add(hit.getLocation().subtract(client.player.getEyePosition()).normalize().scale(0.001));
        BlockHitResult actual = client.level.clip(new ClipContext(client.player.getEyePosition(), beyond,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
        return actual.getType() == HitResult.Type.BLOCK && actual.getBlockPos().equals(hit.getBlockPos());
    }

    static boolean explosionGate(Minecraft client, Vec3 center, int health, int distance) {
        return client.player.getHealth() + client.player.getAbsorptionAmount() >= health
            && client.player.position().distanceToSqr(center) >= (double)distance * distance;
    }

    static boolean acquire(Minecraft client, InventoryActionScheduler.Owner owner) {
        return InventoryActionScheduler.shared().tryAcquire(owner,
            InventoryActionScheduler.Channel.HOTBAR_SELECTION, client.player.tickCount, 1);
    }

    static int findHotbar(Minecraft client, Predicate<ItemStack> predicate) {
        int selected = client.player.getInventory().getSelectedSlot();
        if (predicate.test(client.player.getInventory().getItem(selected))) return selected;
        for (int slot = 0; slot < 9; slot++) if (predicate.test(client.player.getInventory().getItem(slot))) return slot;
        return -1;
    }

    static boolean withSlot(Minecraft client, InventoryActionScheduler.Owner owner, int slot, BooleanSupplier action) {
        if (slot < 0 || slot > 8) return false;
        LocalPlayer player = client.player;
        int original = player.getInventory().getSelectedSlot();
        if (!InventoryAutomationSupport.selectHotbar(client, player, owner, slot, 1)) return false;
        try {
            return action.getAsBoolean();
        } finally {
            if (client.player == player && player.getInventory().getSelectedSlot() == slot
                && InventoryActionScheduler.shared().isOwnedBy(owner,
                    InventoryActionScheduler.Channel.HOTBAR_SELECTION, player.tickCount)) {
                InventoryAutomationSupport.selectHotbar(client, player, owner, original, 1);
            }
            InventoryActionScheduler.shared().releaseAll(owner);
        }
    }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        CombatAdditionsConfig c = config.combatAdditions;
        return List.of(
            GuiModule.toggle("Aim Assist", "Smooths yaw and pitch toward visible players inside your aim cone while attack is physically held; skips teammates and spectators.", () -> c.aimAssist, value -> c.aimAssist = value)
                .with(new GuiSetting.Slider("Aim cone", () -> c.aimFov, value -> c.aimFov = value, 10, 120, " deg"))
                .with(new GuiSetting.Slider("Range", () -> c.aimRange, value -> c.aimRange = value, 1, 6, "m"))
                .with(new GuiSetting.Slider("Turn limit", () -> c.aimDegreesPerTick, value -> c.aimDegreesPerTick = value, 1, 10, " deg/t"))
                .build(),
            GuiModule.toggle("Auto Clicker", "Repeats vanilla attacks on a valid crosshair target while attack is physically held. Respects weapon charge by default.", () -> c.autoClicker, value -> c.autoClicker = value)
                .with(new GuiSetting.Slider("Maximum CPS", () -> c.clickerCps, value -> c.clickerCps = value, 1, 12, " CPS"))
                .with(new GuiSetting.Toggle("Ignore cooldown", () -> c.clickerIgnoreCooldown, value -> c.clickerIgnoreCooldown = value))
                .build(),
            GuiModule.toggle("Auto Crystal", "Hold attack to break nearby visible crystals; hold use on clear obsidian or bedrock to place from your hotbar. Uses normal reach and health/distance gates.", () -> c.autoCrystal, value -> c.autoCrystal = value)
                .with(new GuiSetting.Slider("Action delay", () -> c.crystalDelayTicks, value -> c.crystalDelayTicks = value, 4, 40, "t"))
                .with(new GuiSetting.Slider("Minimum health", () -> c.crystalMinimumHealth, value -> c.crystalMinimumHealth = value, 8, 36, " HP"))
                .with(new GuiSetting.Slider("Self distance", () -> c.crystalMinimumDistance, value -> c.crystalMinimumDistance = value, 2, 6, "m"))
                .with(new GuiSetting.Info("Safety", () -> "Health/range gates; no damage prediction"))
                .build(),
            GuiModule.toggle("Shield Breaker", "Temporarily selects a usable hotbar axe for an in-range target actively blocking with a shield, then restores your slot.", () -> c.shieldBreaker, value -> c.shieldBreaker = value).build(),
            GuiModule.toggle("Mace Bomber", "Times one charged mace attack per descent on a living entity under your crosshair and within normal reach. Auto mace equips early in the fall so its attack can charge.", () -> c.maceBomber, value -> c.maceBomber = value)
                .with(new GuiSetting.Slider("Minimum fall", () -> c.maceBomberFallDistance, value -> c.maceBomberFallDistance = value, 2, 40, "m"))
                .with(new GuiSetting.Toggle("Auto hotbar mace", () -> c.maceBomberAutoMace, value -> c.maceBomberAutoMace = value))
                .with(new GuiSetting.Toggle("Restore slot", () -> c.maceBomberRestore, value -> c.maceBomberRestore = value))
                .with(new GuiSetting.Toggle("Require attack held", () -> c.maceBomberRequireAttack, value -> c.maceBomberRequireAttack = value))
                .with(new GuiSetting.Info("Attack", () -> "Once per descent; normal cooldown"))
                .build(),
            GuiModule.toggle("Anchor Macro", "Hold use while aiming at an existing anchor to charge once and detonate. One anchor per hold; disabled where anchors set spawn.", () -> c.anchorMacro, value -> c.anchorMacro = value)
                .with(new GuiSetting.Slider("Step delay", () -> c.anchorDelayTicks, value -> c.anchorDelayTicks = value, 4, 40, "t"))
                .with(new GuiSetting.Slider("Minimum health", () -> c.anchorMinimumHealth, value -> c.anchorMinimumHealth = value, 8, 36, " HP"))
                .with(new GuiSetting.Slider("Self distance", () -> c.anchorMinimumDistance, value -> c.anchorMinimumDistance = value, 2, 6, "m"))
                .with(new GuiSetting.Info("Safety", () -> "Health/range gates; no damage prediction"))
                .build(),
            GuiModule.toggle("Double Anchor", "Allows two existing anchors per held use: aim at the second after the first disappears. Shares Anchor Macro safety settings; does not place anchors.", () -> c.doubleAnchor, value -> c.doubleAnchor = value).build()
        );
    }
}
