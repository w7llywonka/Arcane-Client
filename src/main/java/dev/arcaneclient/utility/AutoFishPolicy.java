package dev.arcaneclient.utility;

/** Pure bite/recast timing state machine for Auto Fish. */
public final class AutoFishPolicy {
    static final int SETTLED_WATER_TICKS = 3;

    public enum Action {
        NONE,
        CAST,
        REEL
    }

    public record Settings(int castDelayTicks, int recastDelayTicks, double biteVelocityThreshold) {
        public Settings {
            if (castDelayTicks < 0 || recastDelayTicks < 0) {
                throw new IllegalArgumentException("delays cannot be negative");
            }
            if (biteVelocityThreshold >= 0.0) {
                throw new IllegalArgumentException("biteVelocityThreshold must be downward/negative");
            }
        }
    }

    private long nextActionTick;
    private int settledWaterTicks;
    private boolean biteArmed;
    private boolean belowBiteThreshold;

    public Action decide(
        long tick,
        boolean enabled,
        boolean rodAvailable,
        boolean bobberPresent,
        boolean bobberInWater,
        boolean hookedEntity,
        double bobberVerticalVelocity,
        Settings settings
    ) {
        if (!enabled || !rodAvailable) {
            reset(tick);
            return Action.NONE;
        }
        if (bobberPresent) {
            if (hookedEntity && tick >= nextActionTick) {
                return reel(tick, settings);
            }
            if (!bobberInWater) {
                resetBobberObservation();
                return Action.NONE;
            }

            boolean belowThreshold = bobberVerticalVelocity <= settings.biteVelocityThreshold();
            if (!biteArmed) {
                // A cast can have a large downward velocity before and during its
                // first splash. Arm only after the bobber has remained in water
                // above the bite threshold for several consecutive observations.
                settledWaterTicks = belowThreshold ? 0 : settledWaterTicks + 1;
                if (settledWaterTicks >= SETTLED_WATER_TICKS) {
                    biteArmed = true;
                    belowBiteThreshold = false;
                }
                return Action.NONE;
            }

            boolean crossedDownward = !belowBiteThreshold && belowThreshold;
            belowBiteThreshold = belowThreshold;
            if (crossedDownward && tick >= nextActionTick) {
                return reel(tick, settings);
            }
            return Action.NONE;
        }
        resetBobberObservation();
        if (tick >= nextActionTick) {
            nextActionTick = tick + Math.max(1, settings.castDelayTicks());
            return Action.CAST;
        }
        return Action.NONE;
    }

    public void reset(long tick) {
        nextActionTick = tick;
        resetBobberObservation();
    }

    private Action reel(long tick, Settings settings) {
        nextActionTick = tick + settings.recastDelayTicks();
        resetBobberObservation();
        return Action.REEL;
    }

    private void resetBobberObservation() {
        settledWaterTicks = 0;
        biteArmed = false;
        belowBiteThreshold = false;
    }
}
