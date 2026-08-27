package dev.arcaneclient.addon;

import dev.arcaneclient.ArcaneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;

@Environment(EnvType.CLIENT)
public final class HissAddon {
    private static final long DURATION_NANOS = 2_600_000_000L;
    private static final int SEGMENT_COUNT = 18;
    private static final int SEGMENT_SPACING = 8;
    private static final String TITLE = "HISS ADDON";
    private static final String SUBTITLE = "ARCANE CLIENT";

    private static long startedAt = -1L;
    private static boolean finished;

    private HissAddon() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen) || finished) {
                return;
            }
            if (startedAt < 0L) {
                startedAt = System.nanoTime();
            }
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
                render(client, graphics)
            );
        });
        ArcaneClient.LOGGER.info("Hiss Addon startup animation registered");
    }

    private static void render(MinecraftClient client, DrawContext graphics) {
        double progress = (System.nanoTime() - startedAt) / (double)DURATION_NANOS;
        if (progress >= 1.0) {
            finished = true;
            return;
        }

        float opacity = visibility(progress);
        int width = graphics.getScaledWindowWidth();
        int height = graphics.getScaledWindowHeight();
        int centerY = height / 2 - 14;

        graphics.fill(0, 0, width, height, color(0x0B0B11, Math.round(198.0F * opacity)));

        double travel = smoothStep(progress);
        int headX = (int)Math.round(-24.0 + travel * (width + SEGMENT_COUNT * SEGMENT_SPACING + 48.0));
        double phase = progress * Math.PI * 10.0;

        for (int segment = SEGMENT_COUNT - 1; segment >= 0; segment--) {
            int x = headX - segment * SEGMENT_SPACING;
            int y = centerY + (int)Math.round(Math.sin(phase - segment * 0.72) * 8.0);
            int radius = segment == 0 ? 5 : 3;
            int rgb = segment == 0 ? 0x4B83FF : 0x8B5CF6;
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color(rgb, Math.round(255.0F * opacity)));
        }

        if (headX >= 0 && headX < width) {
            int eyeY = centerY + (int)Math.round(Math.sin(phase) * 8.0) - 2;
            graphics.fill(headX + 1, eyeY, headX + 3, eyeY + 2, color(0xF8FAFC, Math.round(255.0F * opacity)));
        }

        int titleColor = color(0xF8FAFC, Math.round(255.0F * opacity));
        int subtitleColor = color(0x8B5CF6, Math.round(255.0F * opacity));
        graphics.drawCenteredTextWithShadow(client.textRenderer, TITLE, width / 2, centerY + 31, titleColor);
        graphics.drawCenteredTextWithShadow(client.textRenderer, SUBTITLE, width / 2, centerY + 45, subtitleColor);
    }

    private static float visibility(double progress) {
        double fadeIn = clamp(progress / 0.12);
        double fadeOut = clamp((1.0 - progress) / 0.20);
        return (float)Math.min(fadeIn, fadeOut);
    }

    private static double smoothStep(double value) {
        double clamped = clamp(value);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int color(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }
}
