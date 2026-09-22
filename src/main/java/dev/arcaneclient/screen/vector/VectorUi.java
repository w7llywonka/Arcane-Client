package dev.arcaneclient.screen.vector;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Arcane-owned vector UI; extraction and immediate OpenGL rendering remain separate. */
public final class VectorUi {
    private static final ArrayList<VectorUiFrame.Command> commands = new ArrayList<>();
    private static long generation;
    private static long context;
    private static final ByteBuffer[] fontData = new ByteBuffer[2];
    private static final int[] fonts = {-1, -1};
    private static int font = -1;
    private static int pendingFont = -1;
    private static int framebuffer;
    private static int stencilBuffer;
    private static int stencilWidth;
    private static int stencilHeight;
    private static boolean failed;
    private static boolean recording;
    private static int clipDepth;
    private static GuiGraphicsExtractor extractionContext;
    private static Screen owner;
    private static int width;
    private static int height;
    private static VectorUiFrame pending;
    private static boolean renderedFrameLogged;

    private VectorUi() {}

    /** Called before GUI extraction so an early-return frame cannot replay old UI. */
    public static void startFrame() {
        generation++;
        recording = false;
        pending = null;
        commands.clear();
        extractionContext = null;
        owner = null;
        clipDepth = 0;
    }

    public static boolean begin(GuiGraphicsExtractor drawContext, int logicalWidth, int logicalHeight) {
        Minecraft client = Minecraft.getInstance();
        if (drawContext == null || logicalWidth <= 0 || logicalHeight <= 0 || failed
            || !(client.gui.screen() instanceof ArcaneSettingsScreen) || !RenderSystem.isOnRenderThread()) return false;
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        if (target == null || !(target.getColorTexture() instanceof GlTexture)) return false;
        if (!initialize()) return false;
        font = fonts[ArcaneClient.config() != null && ArcaneClient.config().uiFont == 1 ? 1 : 0];
        commands.clear();
        pending = null;
        extractionContext = drawContext;
        owner = client.gui.screen();
        width = logicalWidth;
        height = logicalHeight;
        clipDepth = 0;
        recording = true;
        return true;
    }

    public static boolean recording() { return recording; }

    public static void finish() {
        if (!recording) return;
        while (clipDepth > 0) popClip();
        pending = new VectorUiFrame(generation, owner, extractionContext, width, height, commands);
        pendingFont = font;
        recording = false;
        commands.clear();
    }

    public static void rect(float x, float y, float w, float h, float radius, int argb) {
        if (recording && w > 0 && h > 0) commands.add(new VectorUiFrame.Rect(x, y, w, h, radius, argb));
    }
    public static void outline(float x, float y, float w, float h, float radius, float thickness, int argb) {
        if (recording && w > 0 && h > 0 && thickness > 0) commands.add(new VectorUiFrame.Outline(x, y, w, h, radius, thickness, argb));
    }
    public static void text(String value, float x, float y, float size, int argb) {
        if (recording && value != null && !value.isEmpty() && size > 0) commands.add(new VectorUiFrame.Label(value, x, y, size, argb));
    }
    public static void line(float x1, float y1, float x2, float y2, float thickness, int argb) {
        if (recording && thickness > 0) commands.add(new VectorUiFrame.Line(x1, y1, x2, y2, thickness, argb));
    }
    public static void pushClip(float x, float y, float w, float h) {
        if (!recording) return;
        commands.add(new VectorUiFrame.PushClip(x, y, Math.max(0, w), Math.max(0, h)));
        clipDepth++;
    }
    public static void popClip() {
        if (!recording || clipDepth <= 0) return;
        commands.add(new VectorUiFrame.PopClip());
        clipDepth--;
    }
    public static void gradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        if (recording && w > 0 && h > 0) commands.add(new VectorUiFrame.Gradient(x, y, w, h, radius, top, bottom));
    }
    public static void shadow(float x, float y, float w, float h, float radius, float spread, int argb) {
        if (recording && w > 0 && h > 0 && spread > 0) commands.add(new VectorUiFrame.Shadow(x, y, w, h, radius, spread, argb));
    }

    public static float textWidth(String value, float size) {
        if (context == 0 || failed || value == null || !RenderSystem.isOnRenderThread()) return -1.0f;
        if (value.isEmpty()) return 0.0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            textStyle(size);
            return NanoVG.nvgTextBounds(context, 0.0f, 0.0f, value, stack.mallocFloat(4));
        } catch (RuntimeException | LinkageError error) {
            fail(error);
            return -1.0f;
        }
    }

    /** Runs after Minecraft has actually submitted its deferred DrawContext commands. */
    public static void renderPending() {
        VectorUiFrame frame = pending;
        pending = null;
        if (frame == null || failed || frame.generation() != generation || !RenderSystem.isOnRenderThread()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() != frame.owner() || client.gui.overlay() != null) return;
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        if (target == null || !(target.getColorTexture() instanceof GlTexture texture)
            || target.width <= 0 || target.height <= 0) return;

        VectorGlState saved = new VectorGlState();
        font = pendingFont;
        boolean begun = false;
        try {
            attachTarget(texture.glId(), target.width, target.height);
            GL33C.glViewport(0, 0, target.width, target.height);
            GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
            GL33C.glDisable(GL33C.GL_DEPTH_TEST);
            GL33C.glDepthMask(false);
            GL33C.glColorMask(true, true, true, true);
            GL33C.glStencilMask(0xFF);
            GL33C.glClearStencil(0);
            GL33C.glClear(GL33C.GL_STENCIL_BUFFER_BIT);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindSampler(0, 0);
            float pixelRatio = (float)target.width / frame.width();
            NanoVG.nvgBeginFrame(context, frame.width(), frame.height(), pixelRatio);
            begun = true;
            for (VectorUiFrame.Command command : frame.commands()) draw(command);
            NanoVG.nvgEndFrame(context);
            begun = false;
            if (!renderedFrameLogged) {
                ArcaneClient.LOGGER.info("Arcane vector UI rendered {} commands after GUI frame completion", frame.commands().size());
                renderedFrameLogged = true;
            }
        } catch (RuntimeException | LinkageError error) {
            if (begun) {
                try { NanoVG.nvgCancelFrame(context); } catch (RuntimeException | LinkageError ignored) {}
                begun = false;
            }
            fail(error);
        } finally {
            if (begun) NanoVG.nvgCancelFrame(context);
            saved.restore();
        }
    }

    public static void close() {
        recording = false;
        pending = null;
        commands.clear();
        if (context != 0) {
            NanoVGGL3.nvgDelete(context);
            context = 0;
        }
        for (int index = 0; index < fontData.length; index++) {
            if (fontData[index] != null) MemoryUtil.memFree(fontData[index]);
            fontData[index] = null;
            fonts[index] = -1;
        }
        if (framebuffer != 0) GL33C.glDeleteFramebuffers(framebuffer);
        if (stencilBuffer != 0) GL33C.glDeleteRenderbuffers(stencilBuffer);
        framebuffer = stencilBuffer = stencilWidth = stencilHeight = 0;
        font = -1;
        renderedFrameLogged = false;
    }

    private static boolean initialize() {
        if (context != 0) return true;
        VectorGlState saved = null;
        try {
            if (!GL.getCapabilities().OpenGL33) return false;
            saved = new VectorGlState();
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindSampler(0, 0);
            context = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
            if (context == 0) throw new IllegalStateException("Could not initialize the vector UI context");
            loadFont(0, "xuong");
            loadFont(1, "sora");
            font = fonts[0];
            ArcaneClient.LOGGER.info("Arcane vector UI initialized");
            return true;
        } catch (IOException | RuntimeException | LinkageError error) {
            fail(error);
            try { close(); } catch (RuntimeException | LinkageError ignored) {}
            return false;
        } finally {
            if (saved != null) saved.restore();
        }
    }

    private static void fail(Throwable error) {
        if (!failed) ArcaneClient.LOGGER.warn("Vector UI unavailable; using the standard UI renderer", error);
        failed = true;
        recording = false;
        pending = null;
    }

    private static void loadFont(int index, String name) throws IOException {
        try (InputStream stream = VectorUi.class.getResourceAsStream("/assets/arcaneclient/font/" + name + ".ttf")) {
            if (stream == null) throw new IOException("Bundled UI font is missing: " + name);
            byte[] bytes = stream.readAllBytes();
            fontData[index] = MemoryUtil.memAlloc(bytes.length);
            fontData[index].put(bytes).flip();
            fonts[index] = NanoVG.nvgCreateFontMem(context, "arcane-" + name, fontData[index], false);
            if (fonts[index] < 0) throw new IOException("Bundled UI font could not be loaded: " + name);
        }
    }

    private static void attachTarget(int texture, int targetWidth, int targetHeight) {
        if (framebuffer == 0) framebuffer = GL33C.glGenFramebuffers();
        if (stencilBuffer == 0) stencilBuffer = GL33C.glGenRenderbuffers();
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, framebuffer);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, texture, 0);
        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, stencilBuffer);
        if (stencilWidth != targetWidth || stencilHeight != targetHeight) {
            GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_STENCIL_INDEX8, targetWidth, targetHeight);
            stencilWidth = targetWidth;
            stencilHeight = targetHeight;
        }
        GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_STENCIL_ATTACHMENT, GL33C.GL_RENDERBUFFER, stencilBuffer);
        GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0);
        if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Vector UI framebuffer is incomplete");
        }
    }

    private static void draw(VectorUiFrame.Command command) {
        if (command instanceof VectorUiFrame.PushClip clip) {
            NanoVG.nvgSave(context);
            NanoVG.nvgIntersectScissor(context, clip.x(), clip.y(), clip.width(), clip.height());
            return;
        }
        if (command instanceof VectorUiFrame.PopClip) {
            NanoVG.nvgRestore(context);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            switch (command) {
                case VectorUiFrame.Rect r -> {
                    rounded(r.x(), r.y(), r.width(), r.height(), r.radius());
                    NanoVG.nvgFillColor(context, color(stack, r.color()));
                    NanoVG.nvgFill(context);
                }
                case VectorUiFrame.Outline r -> {
                    float inset = Math.min(r.thickness(), Math.min(r.width(), r.height())) * 0.5f;
                    rounded(r.x() + inset, r.y() + inset, Math.max(0, r.width() - inset * 2), Math.max(0, r.height() - inset * 2), Math.max(0, r.radius() - inset));
                    NanoVG.nvgStrokeWidth(context, r.thickness());
                    NanoVG.nvgStrokeColor(context, color(stack, r.color()));
                    NanoVG.nvgStroke(context);
                }
                case VectorUiFrame.Label label -> {
                    textStyle(label.size());
                    NanoVG.nvgFillColor(context, color(stack, label.color()));
                    NanoVG.nvgText(context, label.x(), label.y(), label.value());
                }
                case VectorUiFrame.Line line -> {
                    NanoVG.nvgBeginPath(context);
                    NanoVG.nvgMoveTo(context, line.x1(), line.y1());
                    NanoVG.nvgLineTo(context, line.x2(), line.y2());
                    NanoVG.nvgLineCap(context, NanoVG.NVG_ROUND);
                    NanoVG.nvgLineJoin(context, NanoVG.NVG_ROUND);
                    NanoVG.nvgStrokeWidth(context, line.thickness());
                    NanoVG.nvgStrokeColor(context, color(stack, line.color()));
                    NanoVG.nvgStroke(context);
                }
                case VectorUiFrame.Gradient r -> {
                    NVGPaint paint = NVGPaint.malloc(stack);
                    NanoVG.nvgLinearGradient(context, r.x(), r.y(), r.x(), r.y() + r.height(), color(stack, r.top()), color(stack, r.bottom()), paint);
                    rounded(r.x(), r.y(), r.width(), r.height(), r.radius());
                    NanoVG.nvgFillPaint(context, paint);
                    NanoVG.nvgFill(context);
                }
                case VectorUiFrame.Shadow r -> {
                    NVGPaint paint = NVGPaint.malloc(stack);
                    NanoVG.nvgBoxGradient(context, r.x(), r.y(), r.width(), r.height(), r.radius(), r.spread(), color(stack, r.color()), color(stack, r.color() & 0x00FFFFFF), paint);
                    rounded(r.x() - r.spread(), r.y() - r.spread(), r.width() + r.spread() * 2, r.height() + r.spread() * 2, r.radius() + r.spread());
                    NanoVG.nvgFillPaint(context, paint);
                    NanoVG.nvgFill(context);
                }
                default -> {}
            }
        }
    }

    private static void rounded(float x, float y, float w, float h, float radius) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgRoundedRect(context, x, y, w, h, Math.max(0, Math.min(radius, Math.min(w, h) * 0.5f)));
    }

    private static void textStyle(float size) {
        NanoVG.nvgFontFaceId(context, font);
        NanoVG.nvgFontSize(context, size);
        NanoVG.nvgFontBlur(context, 0.0f);
        NanoVG.nvgTextLetterSpacing(context, 0.0f);
        NanoVG.nvgTextAlign(context, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP);
    }

    private static NVGColor color(MemoryStack stack, int argb) {
        return NVGColor.malloc(stack)
            .r((argb >>> 16 & 255) / 255.0f)
            .g((argb >>> 8 & 255) / 255.0f)
            .b((argb & 255) / 255.0f)
            .a((argb >>> 24) / 255.0f);
    }
}
