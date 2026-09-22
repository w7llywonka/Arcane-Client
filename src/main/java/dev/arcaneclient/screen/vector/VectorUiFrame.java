package dev.arcaneclient.screen.vector;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Immutable UI instructions extracted before Minecraft submits its GUI render pass. */
record VectorUiFrame(long generation, Screen owner, GuiGraphicsExtractor context, int width, int height, List<Command> commands) {
    VectorUiFrame {
        commands = List.copyOf(commands);
    }

    sealed interface Command permits Rect, Outline, Label, Line, PushClip, PopClip, Gradient, Shadow {}
    record Rect(float x, float y, float width, float height, float radius, int color) implements Command {}
    record Outline(float x, float y, float width, float height, float radius, float thickness, int color) implements Command {}
    record Label(String value, float x, float y, float size, int color) implements Command {}
    record Line(float x1, float y1, float x2, float y2, float thickness, int color) implements Command {}
    record PushClip(float x, float y, float width, float height) implements Command {}
    record PopClip() implements Command {}
    record Gradient(float x, float y, float width, float height, float radius, int top, int bottom) implements Command {}
    record Shadow(float x, float y, float width, float height, float radius, float spread, int color) implements Command {}
}
