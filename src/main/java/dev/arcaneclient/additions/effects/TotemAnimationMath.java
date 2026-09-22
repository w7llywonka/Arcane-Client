package dev.arcaneclient.additions.effects;

/** Pure animation curves. All movement is in screen space, not player or camera space. */
public final class TotemAnimationMath {
    public record Frame(float scale, float rotation, float slideY, float opacity) { }

    private TotemAnimationMath() { }

    public static Frame frame(int style, float age, int duration) {
        float progress = Math.clamp((Float.isFinite(age) ? age : 0) / Math.clamp(duration, 6, 80), 0.0f, 1.0f);
        float eased = 1.0f - (float)Math.pow(1.0f - progress, 3);
        float fadeIn = Math.clamp(progress / 0.12f, 0.0f, 1.0f);
        float fadeOut = Math.clamp((1.0f - progress) / 0.28f, 0.0f, 1.0f);
        float opacity = Math.min(fadeIn, fadeOut);
        return switch (Math.clamp(style, 0, 2)) {
            case 0 -> new Frame(0.65f + eased * 0.35f, (1.0f - eased) * (float)(-Math.PI * 1.5), 0, opacity);
            case 1 -> new Frame(1.0f, 0, (1.0f - eased) * 90.0f, opacity);
            default -> new Frame(0.92f + eased * 0.08f, 0, 0, opacity);
        };
    }
}
