package dev.arcaneclient.combat;

import java.util.Objects;

/** Pure target and timing gate for Trigger Bot. */
public final class TriggerBotPolicy {
    public enum TargetKind {
        PLAYER,
        HOSTILE,
        PASSIVE,
        OTHER
    }

    public record Settings(
        float minimumAttackCharge,
        int minimumDelayTicks,
        boolean targetPlayers,
        boolean targetHostiles,
        boolean targetPassives,
        boolean targetOther,
        boolean ignoreCreativePlayers,
        boolean ignoreTamed,
        boolean requireLineOfSight,
        boolean pauseWhileUsingItem
    ) {
        public Settings {
            if (minimumAttackCharge < 0.0f || minimumAttackCharge > 1.0f) {
                throw new IllegalArgumentException("minimumAttackCharge must be between 0 and 1");
            }
            if (minimumDelayTicks < 0) throw new IllegalArgumentException("minimumDelayTicks cannot be negative");
        }
    }

    public record Context(
        TargetKind targetKind,
        boolean alive,
        boolean attackable,
        boolean self,
        boolean protectedTarget,
        boolean creativePlayer,
        boolean tamed,
        boolean visible,
        boolean usingItem,
        boolean screenOpen,
        boolean spectator,
        float attackCharge,
        int ticksSinceAttack
    ) {
        public Context {
            Objects.requireNonNull(targetKind, "targetKind");
        }
    }

    private TriggerBotPolicy() {
    }

    public static boolean shouldAttack(Context context, Settings settings) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(settings, "settings");
        if (!context.alive() || !context.attackable() || context.self() || context.protectedTarget()) return false;
        if (context.screenOpen() || context.spectator()) return false;
        if (context.creativePlayer() && settings.ignoreCreativePlayers()) return false;
        if (context.tamed() && settings.ignoreTamed()) return false;
        if (!context.visible() && settings.requireLineOfSight()) return false;
        if (context.usingItem() && settings.pauseWhileUsingItem()) return false;
        if (context.attackCharge() < settings.minimumAttackCharge()) return false;
        if (context.ticksSinceAttack() < settings.minimumDelayTicks()) return false;
        return switch (context.targetKind()) {
            case PLAYER -> settings.targetPlayers();
            case HOSTILE -> settings.targetHostiles();
            case PASSIVE -> settings.targetPassives();
            case OTHER -> settings.targetOther();
        };
    }
}
