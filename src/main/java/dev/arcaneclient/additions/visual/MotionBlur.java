package dev.arcaneclient.additions.visual;

import dev.arcaneclient.ArcaneClient;

/**
 * Compatibility gate for the legacy OpenGL motion-blur pass.
 *
 * <p>Minecraft 26.3 can run on RenderPearl's OpenGL or Vulkan backend and no longer exposes the
 * GL framebuffer/texture implementation classes used by the 1.21.11 pass. Keeping raw GL calls
 * here would corrupt the active backend. The setting therefore remains readable, but the pass is
 * intentionally disabled until it can be implemented as a backend-neutral RenderPearl effect.</p>
 */
public final class MotionBlur {
    private MotionBlur() { }

    public static String status() {
        return ArcaneClient.config() != null && ArcaneClient.config().visualAdditions.motionBlur
            ? "Unavailable on the 26.3 renderer" : "Off";
    }

    public static void onToggle() { }
    public static void reset() { }
    public static void beginFrame(boolean renderWorld) { }
    public static void renderWorld() { }
    public static void close() { }
}
