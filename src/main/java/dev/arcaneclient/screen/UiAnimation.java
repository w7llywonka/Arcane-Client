package dev.arcaneclient.screen;

/**
 * Frame-rate independent interface easing. Every animated surface in the Click GUI moves through a
 * {@link Track}, so a switch travels the same arc at 30 and at 300 frames per second.
 */
public final class UiAnimation {
    /** Toggle switches and their knobs. */
    public static final float SWITCH_SPEED = 17.0f;
    /** Hover highlights, which should trail the cursor slightly rather than snap. */
    public static final float HOVER_SPEED = 14.0f;
    /** The longest frame an animation reacts to, so returning to a paused window never snaps. */
    static final float MAX_STEP_SECONDS = 0.1f;
    private static final float SETTLE = 0.002f;

    private UiAnimation() {
    }

    /** Exponential smoothing: the remaining distance is closed at a fixed rate per second. */
    public static float approach(float current, float target, float deltaSeconds, float speed) {
        if (deltaSeconds <= 0.0f || speed <= 0.0f) {
            return current;
        }
        float step = Math.min(deltaSeconds, MAX_STEP_SECONDS);
        float blend = 1.0f - (float) Math.exp(-speed * step);
        float next = current + (target - current) * blend;
        return Math.abs(target - next) <= SETTLE ? target : next;
    }

    /** Smoothstep, so a sliding knob leaves and lands without a visible corner. */
    public static float ease(float fraction) {
        float clamped = Math.clamp(fraction, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    /**
     * One animated boolean. The first frame adopts its target outright so opening the GUI does not
     * play every enabled module's switch at once.
     */
    public static final class Track {
        private float value;
        private boolean primed;

        public float advance(boolean target, float deltaSeconds, float speed) {
            float goal = target ? 1.0f : 0.0f;
            if (!this.primed) {
                this.primed = true;
                this.value = goal;
            } else {
                this.value = approach(this.value, goal, deltaSeconds, speed);
            }
            return ease(this.value);
        }
    }
}
