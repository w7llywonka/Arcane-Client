package dev.arcaneclient.screen.vector;

import org.lwjgl.opengl.GL33C;

/**
 * Restores raw driver state after the isolated vector pass. Minecraft's GL cache
 * remains untouched: every driver value changed by this pass is restored.
 */
final class VectorGlState {
    private final int program = integer(GL33C.GL_CURRENT_PROGRAM);
    private final int vao = integer(GL33C.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = integer(GL33C.GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = integer(GL33C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final int uniformBuffer = integer(GL33C.GL_UNIFORM_BUFFER_BINDING);
    private final int uniformBase = GL33C.glGetIntegeri(GL33C.GL_UNIFORM_BUFFER_BINDING, 0);
    private final long uniformStart = GL33C.glGetInteger64i(GL33C.GL_UNIFORM_BUFFER_START, 0);
    private final long uniformSize = GL33C.glGetInteger64i(GL33C.GL_UNIFORM_BUFFER_SIZE, 0);
    private final int unpackBuffer = integer(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int drawFramebuffer = integer(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = integer(GL33C.GL_READ_FRAMEBUFFER_BINDING);
    private final int renderbuffer = integer(GL33C.GL_RENDERBUFFER_BINDING);
    private final int activeTexture = integer(GL33C.GL_ACTIVE_TEXTURE);
    private final int texture0;
    private final int sampler0 = GL33C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, 0);
    private final boolean blend = enabled(GL33C.GL_BLEND);
    private final boolean depth = enabled(GL33C.GL_DEPTH_TEST);
    private final boolean stencil = enabled(GL33C.GL_STENCIL_TEST);
    private final boolean scissor = enabled(GL33C.GL_SCISSOR_TEST);
    private final boolean cull = enabled(GL33C.GL_CULL_FACE);
    private final int blendSrcRgb = integer(GL33C.GL_BLEND_SRC_RGB);
    private final int blendDstRgb = integer(GL33C.GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = integer(GL33C.GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = integer(GL33C.GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = integer(GL33C.GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = integer(GL33C.GL_BLEND_EQUATION_ALPHA);
    private final int depthFunction = integer(GL33C.GL_DEPTH_FUNC);
    private final boolean depthWrite = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
    private final int frontFace = integer(GL33C.GL_FRONT_FACE);
    private final int cullMode = integer(GL33C.GL_CULL_FACE_MODE);
    private final int stencilClear = integer(GL33C.GL_STENCIL_CLEAR_VALUE);
    private final StencilFace frontStencil = StencilFace.read(false);
    private final StencilFace backStencil = StencilFace.read(true);
    private final int[] viewport = integers(GL33C.GL_VIEWPORT);
    private final int[] scissorBox = integers(GL33C.GL_SCISSOR_BOX);
    private final int[] colorMask = integers(GL33C.GL_COLOR_WRITEMASK);
    private final int unpackAlignment = integer(GL33C.GL_UNPACK_ALIGNMENT);
    private final int unpackRowLength = integer(GL33C.GL_UNPACK_ROW_LENGTH);
    private final int unpackSkipRows = integer(GL33C.GL_UNPACK_SKIP_ROWS);
    private final int unpackSkipPixels = integer(GL33C.GL_UNPACK_SKIP_PIXELS);

    VectorGlState() {
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        texture0 = integer(GL33C.GL_TEXTURE_BINDING_2D);
        GL33C.glActiveTexture(activeTexture);
    }

    void restore() {
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, renderbuffer);
        GL33C.glUseProgram(program);
        GL33C.glBindVertexArray(vao);
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, arrayBuffer);
        if (vao != 0) GL33C.glBindBuffer(GL33C.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        if (uniformBase != 0 && uniformSize > 0) {
            GL33C.glBindBufferRange(GL33C.GL_UNIFORM_BUFFER, 0, uniformBase, uniformStart, uniformSize);
        } else {
            GL33C.glBindBufferBase(GL33C.GL_UNIFORM_BUFFER, 0, uniformBase);
        }
        GL33C.glBindBuffer(GL33C.GL_UNIFORM_BUFFER, uniformBuffer);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture0);
        GL33C.glBindSampler(0, sampler0);
        GL33C.glActiveTexture(activeTexture);
        set(GL33C.GL_BLEND, blend);
        GL33C.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL33C.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        set(GL33C.GL_DEPTH_TEST, depth);
        GL33C.glDepthFunc(depthFunction);
        GL33C.glDepthMask(depthWrite);
        set(GL33C.GL_CULL_FACE, cull);
        GL33C.glFrontFace(frontFace);
        GL33C.glCullFace(cullMode);
        set(GL33C.GL_STENCIL_TEST, stencil);
        frontStencil.restore(GL33C.GL_FRONT);
        backStencil.restore(GL33C.GL_BACK);
        GL33C.glClearStencil(stencilClear);
        set(GL33C.GL_SCISSOR_TEST, scissor);
        GL33C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL33C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        GL33C.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, unpackAlignment);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH, unpackRowLength);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
    }

    private record StencilFace(int function, int reference, int valueMask, int writeMask, int fail, int depthFail, int pass) {
        static StencilFace read(boolean back) {
            return new StencilFace(
                integer(back ? GL33C.GL_STENCIL_BACK_FUNC : GL33C.GL_STENCIL_FUNC),
                integer(back ? GL33C.GL_STENCIL_BACK_REF : GL33C.GL_STENCIL_REF),
                integer(back ? GL33C.GL_STENCIL_BACK_VALUE_MASK : GL33C.GL_STENCIL_VALUE_MASK),
                integer(back ? GL33C.GL_STENCIL_BACK_WRITEMASK : GL33C.GL_STENCIL_WRITEMASK),
                integer(back ? GL33C.GL_STENCIL_BACK_FAIL : GL33C.GL_STENCIL_FAIL),
                integer(back ? GL33C.GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL33C.GL_STENCIL_PASS_DEPTH_FAIL),
                integer(back ? GL33C.GL_STENCIL_BACK_PASS_DEPTH_PASS : GL33C.GL_STENCIL_PASS_DEPTH_PASS)
            );
        }

        void restore(int face) {
            GL33C.glStencilFuncSeparate(face, function, reference, valueMask);
            GL33C.glStencilMaskSeparate(face, writeMask);
            GL33C.glStencilOpSeparate(face, fail, depthFail, pass);
        }
    }

    private static int integer(int name) { return GL33C.glGetInteger(name); }
    private static boolean enabled(int name) { return GL33C.glIsEnabled(name); }
    private static int[] integers(int name) {
        int[] values = new int[4];
        GL33C.glGetIntegerv(name, values);
        return values;
    }
    private static void set(int capability, boolean on) {
        if (on) GL33C.glEnable(capability);
        else GL33C.glDisable(capability);
    }
}
