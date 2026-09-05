package dev.arcaneclient.combat;

import dev.arcaneclient.freecam.DetachedCameraInteraction;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/** Crosshair-only attack automation with vanilla cooldown and 1.21.11 spear semantics. */
@Environment(EnvType.CLIENT)
public final class TriggerBotController {
    public record Settings(boolean enabled, TriggerBotPolicy.Settings policy) {
        public Settings {
            Objects.requireNonNull(policy, "policy");
        }
    }

    private static long lastAttackTick = Long.MIN_VALUE / 2;

    private TriggerBotController() {
    }

    public static boolean tick(MinecraftClient client, Settings settings) {
        return tick(client, settings, entity -> false, entity -> { });
    }

    /**
     * @param protectedTarget returns true for friends/other protected targets
     * @param prepareWeapon synchronously applies Mace/Spear/Smart Weapon precedence
     */
    public static boolean tick(
        MinecraftClient client,
        Settings settings,
        Predicate<Entity> protectedTarget,
        Consumer<Entity> prepareWeapon
    ) {
        Objects.requireNonNull(protectedTarget, "protectedTarget");
        Objects.requireNonNull(prepareWeapon, "prepareWeapon");
        ClientPlayerEntity player = client.player;
        if (!settings.enabled() || player == null || client.world == null || client.interactionManager == null) return false;
        if (DetachedCameraInteraction.isActive()) return false;
        if (!(client.crosshairTarget instanceof EntityHitResult hit)) return false;
        Entity target = hit.getEntity();
        long tick = player.age;
        int elapsed = lastAttackTick <= Long.MIN_VALUE / 4
            ? Integer.MAX_VALUE
            : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tick - lastAttackTick));
        TriggerBotPolicy.Context context = new TriggerBotPolicy.Context(
            targetKind(target),
            target.isAlive(),
            target.isAttackable(),
            target == player,
            protectedTarget.test(target),
            target instanceof PlayerEntity targetPlayer && targetPlayer.isCreative(),
            target instanceof TameableEntity tameable && tameable.isTamed(),
            player.canSee(target),
            player.isUsingItem(),
            client.currentScreen != null,
            player.isSpectator(),
            player.getAttackCooldownProgress(0.0f),
            elapsed
        );
        if (!TriggerBotPolicy.shouldAttack(context, settings.policy())) return false;

        prepareWeapon.accept(target);
        ItemStack weapon = player.getMainHandStack();
        if (player.isBelowMinimumAttackCharge(weapon, 0)) return false;
        PiercingWeaponComponent piercing = weapon.get(DataComponentTypes.PIERCING_WEAPON);
        if (piercing != null && !client.interactionManager.isFlyingLocked()) {
            client.interactionManager.attackWithPiercingWeapon(piercing);
        } else {
            AttackRangeComponent range = weapon.get(DataComponentTypes.ATTACK_RANGE);
            if (range != null && !range.isWithinRange(player, hit.getPos())) return false;
            client.interactionManager.attackEntity(player, target);
        }
        player.swingHand(Hand.MAIN_HAND);
        lastAttackTick = tick;
        return true;
    }

    public static void reset() {
        lastAttackTick = Long.MIN_VALUE / 2;
    }

    private static TriggerBotPolicy.TargetKind targetKind(Entity target) {
        if (target instanceof PlayerEntity) return TriggerBotPolicy.TargetKind.PLAYER;
        if (target instanceof HostileEntity) return TriggerBotPolicy.TargetKind.HOSTILE;
        if (target instanceof PassiveEntity) return TriggerBotPolicy.TargetKind.PASSIVE;
        if (target instanceof LivingEntity) return TriggerBotPolicy.TargetKind.OTHER;
        return TriggerBotPolicy.TargetKind.OTHER;
    }
}
