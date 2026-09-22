package dev.arcaneclient.additions.visual;

import org.lwjgl.opengl.GL33C;

/** Snapshots precisely the raw GL state changed by MotionBlur, leaving Minecraft's caches intact. */
final class MotionBlurGlState {
    private final int program = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
    private final int vao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
    private final int unpackBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int drawFramebuffer = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
    private final int activeTexture = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
    private final int[] textures = new int[2];
    private final int[] samplers = new int[2];
    private final int[] viewport = integers(GL33C.GL_VIEWPORT);
    private final int[] colorMask = integers(GL33C.GL_COLOR_WRITEMASK);
    private final int polygonMode = integers(GL33C.GL_POLYGON_MODE)[0];
    private final int blendSrcRgb = GL33C.glGetInteger(GL33C.GL_BLEND_SRC_RGB);
    private final int blendDstRgb = GL33C.glGetInteger(GL33C.GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = GL33C.glGetInteger(GL33C.GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = GL33C.glGetInteger(GL33C.GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = GL33C.glGetInteger(GL33C.GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = GL33C.glGetInteger(GL33C.GL_BLEND_EQUATION_ALPHA);
    private final boolean depthWrite = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
    private final int[] capabilities = {GL33C.GL_BLEND, GL33C.GL_DEPTH_TEST, GL33C.GL_STENCIL_TEST,
        GL33C.GL_SCISSOR_TEST, GL33C.GL_CULL_FACE, GL33C.GL_FRAMEBUFFER_SRGB,
        GL33C.GL_RASTERIZER_DISCARD, GL33C.GL_COLOR_LOGIC_OP};
    private final boolean[] enabled = new boolean[capabilities.length];

    MotionBlurGlState() {
        for (int i = 0; i < capabilities.length; i++) enabled[i] = GL33C.glIsEnabled(capabilities[i]);
        for (int i = 0; i < textures.length; i++) {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + i);
            textures[i] = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            samplers[i] = GL33C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, i);
        }
        GL33C.glActiveTexture(activeTexture);
    }

    void restore() {
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL33C.glUseProgram(program);
        GL33C.glBindVertexArray(vao);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        for (int i = 0; i < textures.length; i++) {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + i);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, textures[i]);
            GL33C.glBindSampler(i, samplers[i]);
        }
        GL33C.glActiveTexture(activeTexture);
        GL33C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL33C.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, polygonMode);
        GL33C.glDepthMask(depthWrite);
        GL33C.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL33C.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        for (int i = 0; i < capabilities.length; i++) {
            if (enabled[i]) GL33C.glEnable(capabilities[i]);
            else GL33C.glDisable(capabilities[i]);
        }
    }

    private static int[] integers(int name) {
        int[] result = new int[4];
        GL33C.glGetIntegerv(name, result);
        return result;
    }
}
