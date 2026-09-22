package dev.arcaneclient.combat;

import dev.arcaneclient.freecam.DetachedCameraInteraction;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.EntityHitResult;

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

    public static boolean tick(Minecraft client, Settings settings) {
        return tick(client, settings, entity -> false, entity -> { });
    }

    /**
     * @param protectedTarget returns true for friends/other protected targets
     * @param prepareWeapon synchronously applies Mace/Spear/Smart Weapon precedence
     */
    public static boolean tick(
        Minecraft client,
        Settings settings,
        Predicate<Entity> protectedTarget,
        Consumer<Entity> prepareWeapon
    ) {
        Objects.requireNonNull(protectedTarget, "protectedTarget");
        Objects.requireNonNull(prepareWeapon, "prepareWeapon");
        LocalPlayer player = client.player;
        if (!settings.enabled() || player == null || client.level == null || client.gameMode == null) return false;
        if (DetachedCameraInteraction.isActive()) return false;
        if (!(client.hitResult instanceof EntityHitResult hit)) return false;
        Entity target = hit.getEntity();
        long tick = player.tickCount;
        int elapsed = lastAttackTick <= Long.MIN_VALUE / 4
            ? Integer.MAX_VALUE
            : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tick - lastAttackTick));
        TriggerBotPolicy.Context context = new TriggerBotPolicy.Context(
            targetKind(target),
            target.isAlive(),
            target.isAttackable(),
            target == player,
            protectedTarget.test(target),
            target instanceof Player targetPlayer && targetPlayer.isCreative(),
            target instanceof TamableAnimal tameable && tameable.isTame(),
            player.hasLineOfSight(target),
            player.isUsingItem(),
            client.gui.screen() != null,
            player.isSpectator(),
            player.getAttackStrengthScale(0.0f),
            elapsed
        );
        if (!TriggerBotPolicy.shouldAttack(context, settings.policy())) return false;

        prepareWeapon.accept(target);
        ItemStack weapon = player.getMainHandItem();
        if (player.cannotAttackWithItem(weapon, 0)) return false;
        PiercingWeapon piercing = weapon.get(DataComponents.PIERCING_WEAPON);
        if (piercing != null && !client.gameMode.isSpectator()) {
            client.gameMode.piercingAttack(weapon.getAttackAnimation(), piercing);
        } else {
            AttackRange range = weapon.get(DataComponents.ATTACK_RANGE);
            if (range != null && !range.isInRange(player, hit.getLocation())) return false;
            client.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND, weapon.getAttackAnimation(), false);
        }
        lastAttackTick = tick;
        return true;
    }

    public static void reset() {
        lastAttackTick = Long.MIN_VALUE / 2;
    }

    private static TriggerBotPolicy.TargetKind targetKind(Entity target) {
        if (target instanceof Player) return TriggerBotPolicy.TargetKind.PLAYER;
        if (target instanceof Monster) return TriggerBotPolicy.TargetKind.HOSTILE;
        if (target instanceof AgeableMob) return TriggerBotPolicy.TargetKind.PASSIVE;
        if (target instanceof LivingEntity) return TriggerBotPolicy.TargetKind.OTHER;
        return TriggerBotPolicy.TargetKind.OTHER;
    }
}
